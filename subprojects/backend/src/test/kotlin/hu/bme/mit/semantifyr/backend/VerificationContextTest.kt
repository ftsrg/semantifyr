/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backend

import hu.bme.mit.semantifyr.backend.execution.BackendExecutor
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import kotlin.io.path.Path
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class VerificationContextTest {

    private val request = BackendVerificationRequest(
        inlinedOxsts = mock(),
        artifactOutputPath = Path(System.getProperty("java.io.tmpdir")),
    )

    private class ScriptedContext(
        request: BackendVerificationRequest,
        private val behavior: suspend () -> Boolean?,
    ) : VerificationContext<Unit, Boolean>("scripted", request) {
        override val executor = object : BackendExecutor {
            override fun isAvailable(): Boolean {
                return true
            }
        }

        override suspend fun prepare() {
        }

        override suspend fun run(artifacts: Unit): Boolean? {
            return behavior()
        }

        override fun interpret(rawVerdict: Boolean?, artifacts: Unit): VerificationVerdict {
            return when (rawVerdict) {
                true -> VerificationVerdict.Passed
                false -> VerificationVerdict.Failed
                null -> VerificationVerdict.Inconclusive
            }
        }
    }

    @Test
    suspend fun `raw verdict is interpreted`() {
        val result = ScriptedContext(request) { false }.execute()

        assertThat(result.verdict).isEqualTo(VerificationVerdict.Failed)
        assertThat(result.metadata.backendId).isEqualTo("scripted")
    }

    @Test
    suspend fun `BackendUnsupportedException yields NotSupported`() {
        val result = ScriptedContext(request) { throw BackendUnsupportedException("no arrays") }.execute()

        assertThat(result.verdict).isEqualTo(VerificationVerdict.NotSupported)
        assertThat(result.message).isEqualTo("no arrays")
    }

    @Test
    suspend fun `an exception yields Errored`() {
        val result = ScriptedContext(request) { error("tool crashed") }.execute()

        assertThat(result.verdict).isEqualTo(VerificationVerdict.Errored)
        assertThat(result.message).isEqualTo("tool crashed")
    }

    @Test
    suspend fun `a timeout around the run propagates instead of becoming Errored`() {
        val thrown = runCatching {
            withTimeout(50.milliseconds) {
                ScriptedContext(request) {
                    delay(10.seconds)
                    true
                }.execute()
            }
        }.exceptionOrNull()

        assertThat(thrown).isInstanceOf(TimeoutCancellationException::class.java)
    }

    @Test
    fun `Timeout is not decisive`() {
        assertThat(VerificationVerdict.Timeout.isDecisive).isFalse
    }
}
