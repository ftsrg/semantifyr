/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.session

import com.google.inject.Guice
import hu.bme.mit.semantifyr.live.backend.BackendConfig
import hu.bme.mit.semantifyr.live.backend.BackendModule
import hu.bme.mit.semantifyr.live.backend.VerificationConfig
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.eclipse.lsp4j.ExecuteCommandParams
import org.eclipse.lsp4j.jsonrpc.messages.RequestMessage
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

class VerificationGateTest {

    private fun createGate(concurrency: Int = 2, timeoutSeconds: Long = 300): VerificationGate {
        val config = BackendConfig(
            verification = VerificationConfig(concurrency = concurrency, timeout = timeoutSeconds.seconds),
        )
        val injector = Guice.createInjector(BackendModule(config))
        return injector.getInstance(VerificationGate::class.java)
    }

    private fun verifyRequest(id: String = "1"): RequestMessage {
        return RequestMessage().apply {
            this.id = id
            this.method = "workspace/executeCommand"
            this.params = ExecuteCommandParams("oxsts.case.verify", emptyList())
        }
    }

    private fun unrelatedRequest(id: String = "1"): RequestMessage {
        return RequestMessage().apply {
            this.id = id
            this.method = "textDocument/completion"
            this.params = emptyMap<String, Any>()
        }
    }

    @Test
    fun `isVerificationRequest returns true for verify commands`() {
        val gate = createGate()
        assertThat(gate.isVerificationRequest(verifyRequest())).isTrue()
    }

    @Test
    fun `isVerificationRequest returns false for unrelated requests`() {
        val gate = createGate()
        assertThat(gate.isVerificationRequest(unrelatedRequest())).isFalse()
    }

    @Test
    fun `registerVerification acquires a permit and releases on cancel`() = runTest {
        val gate = createGate(concurrency = 2)
        assertThat(gate.availablePermits).isEqualTo(2)

        val job = gate.registerVerification(this) { }
        assertThat(gate.availablePermits).isEqualTo(1)

        job.cancel()
        job.join()
        assertThat(gate.availablePermits).isEqualTo(2)
    }

    @Test
    fun `registerVerification throws when permits are exhausted`() = runTest {
        val gate = createGate(concurrency = 1)

        val job = gate.registerVerification(this) { }
        assertThat(gate.availablePermits).isEqualTo(0)

        assertThatThrownBy {
            gate.registerVerification(this) { }
        }.isInstanceOf(VerificationLimitReachedException::class.java)

        job.cancel()
        job.join()
    }

    @Test
    fun `timeout callback fires and releases permit`() = runTest {
        val gate = createGate(concurrency = 1, timeoutSeconds = 1)
        var timedOut = false

        val job = gate.registerVerification(this) { timedOut = true }

        testScheduler.advanceTimeBy(2.seconds)
        testScheduler.runCurrent()
        job.join()

        assertThat(timedOut).isTrue()
        assertThat(gate.availablePermits).isEqualTo(1)
    }
}
