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
import org.eclipse.lsp4j.ExecuteCommandParams
import org.eclipse.lsp4j.jsonrpc.messages.NotificationMessage
import org.eclipse.lsp4j.jsonrpc.messages.RequestMessage
import org.eclipse.lsp4j.jsonrpc.messages.ResponseMessage
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes

class SessionVerificationManagerTest {

    private fun createGate(concurrency: Int = 2): VerificationGate {
        val config = BackendConfig(
            verification = VerificationConfig(concurrency = concurrency, timeout = 5.minutes),
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

    private fun verifyResponse(id: String = "1"): ResponseMessage {
        return ResponseMessage().apply {
            this.id = id
            this.result = mapOf("status" to "passed")
        }
    }

    private fun notification(): NotificationMessage {
        return NotificationMessage().apply {
            this.method = "textDocument/publishDiagnostics"
            this.params = emptyMap<String, Any>()
        }
    }

    @Test
    fun `unrelated messages pass through without being consumed`() = runTest {
        val gate = createGate()
        val errors = mutableListOf<ResponseMessage>()
        val cancels = mutableListOf<String>()
        val manager = SessionVerificationManager(
            gate = gate,
            scope = this,
            sendCancelToLsp = { cancels += it },
            sendErrorToClient = { errors += it },
        )

        val consumed = manager.handleClientMessage(notification())
        assertThat(consumed).isFalse()
        assertThat(errors).isEmpty()
        assertThat(gate.availablePermits).isEqualTo(2)
    }

    @Test
    fun `verify request acquires a permit and response releases it`() = runTest {
        val gate = createGate()
        val manager = SessionVerificationManager(
            gate = gate,
            scope = this,
            sendCancelToLsp = { },
            sendErrorToClient = { },
        )

        val consumed = manager.handleClientMessage(verifyRequest("42"))
        assertThat(consumed).isFalse()
        assertThat(gate.availablePermits).isEqualTo(1)

        val serverConsumed = manager.handleServerMessage(verifyResponse("42"))
        assertThat(serverConsumed).isFalse()

        testScheduler.runCurrent()
        assertThat(gate.availablePermits).isEqualTo(2)
    }

    @Test
    fun `verify request is consumed when gate is full`() = runTest {
        val gate = createGate(concurrency = 1)
        val errors = mutableListOf<ResponseMessage>()
        val manager = SessionVerificationManager(
            gate = gate,
            scope = this,
            sendCancelToLsp = { },
            sendErrorToClient = { errors += it },
        )

        manager.handleClientMessage(verifyRequest("1"))
        assertThat(gate.availablePermits).isEqualTo(0)

        val consumed = manager.handleClientMessage(verifyRequest("2"))
        assertThat(consumed).isTrue()
        assertThat(errors).hasSize(1)
        assertThat(errors[0].error.code).isEqualTo(-32000)
        assertThat(errors[0].id).isEqualTo("2")
    }

    @Test
    fun `untracked server response is ignored`() = runTest {
        val gate = createGate()
        val manager = SessionVerificationManager(
            gate = gate,
            scope = this,
            sendCancelToLsp = { },
            sendErrorToClient = { },
        )

        val consumed = manager.handleServerMessage(verifyResponse("999"))
        assertThat(consumed).isFalse()
        assertThat(gate.availablePermits).isEqualTo(2)
    }

    @Test
    fun `releaseAll cancels all in-flight jobs and releases permits`() = runTest {
        val gate = createGate(concurrency = 3)
        val manager = SessionVerificationManager(
            gate = gate,
            scope = this,
            sendCancelToLsp = { },
            sendErrorToClient = { },
        )

        manager.handleClientMessage(verifyRequest("1"))
        manager.handleClientMessage(verifyRequest("2"))
        assertThat(gate.availablePermits).isEqualTo(1)

        manager.releaseAll()

        testScheduler.runCurrent()
        assertThat(gate.availablePermits).isEqualTo(3)
    }
}
