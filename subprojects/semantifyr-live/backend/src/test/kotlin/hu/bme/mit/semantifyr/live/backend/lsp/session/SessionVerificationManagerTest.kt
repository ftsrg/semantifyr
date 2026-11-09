/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.lsp.session

import hu.bme.mit.semantifyr.lang.ide.server.wire.VerificationCaseRequest
import hu.bme.mit.semantifyr.live.backend.BackendConfig
import hu.bme.mit.semantifyr.live.backend.VerificationConfig
import hu.bme.mit.semantifyr.live.backend.data.VerificationKind
import hu.bme.mit.semantifyr.live.backend.lsp.service.SessionLanguageClient
import hu.bme.mit.semantifyr.live.backend.lsp.service.SharedExecutorProvider
import hu.bme.mit.semantifyr.live.backend.testing.LspWire
import hu.bme.mit.semantifyr.live.backend.testing.testFlavor
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.future.await
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.util.concurrent.ExecutionException
import java.util.concurrent.atomic.AtomicInteger

class SessionVerificationManagerTest {

    private val registry = SimpleMeterRegistry()
    private val config = BackendConfig(
        verification = VerificationConfig(
            concurrency = 1,
            maxPerSession = 2,
        ),
    )
    private val sharedExecutorProvider = SharedExecutorProvider()
    private val sessionContext = mock<SessionContext> {
        on { sessionId } doReturn "session"
        on { flavor } doReturn testFlavor()
    }
    private val sessionLanguageClient = mock<SessionLanguageClient>()
    private val sessionClient = mock<SessionClient> {
        on { get() } doReturn sessionLanguageClient
    }
    private val manager = SessionVerificationManager(
        config,
        VerificationManager(config, registry),
        sessionContext,
        SessionRunContext(sharedExecutorProvider),
        sessionClient,
        registry,
    )

    @AfterEach
    fun closeExecutor() {
        sharedExecutorProvider.close()
    }

    private fun request(): VerificationCaseRequest {
        return VerificationCaseRequest("file:///workspace/snippet.oxsts", LspWire.range(), "smart-full")
    }

    @Test
    suspend fun `a launch beyond the per-session cap fails without running and is counted by kind`() {
        val release = CompletableDeferred<Unit>()
        val executions = AtomicInteger()
        val blockingWork: suspend () -> Any? = {
            executions.incrementAndGet()
            release.await()
            "verified"
        }

        withSessionScope(sessionContext) {
            val running = manager.launch(request(), VerificationKind.Verify, blockingWork)
            val queued = manager.launch(request(), VerificationKind.Validate, blockingWork)
            val rejected = manager.launch(request(), VerificationKind.Verify, blockingWork)

            assertThat(rejected).isCompletedExceptionally
            assertThatThrownBy {
                rejected.get()
            }.isInstanceOf(ExecutionException::class.java)
                .cause()
                .isInstanceOf(ResponseErrorException::class.java)
                .hasMessageContaining("too many pending verifications")
            assertThat(manager.active()).hasSize(2)
            assertThat(registry.get("semantifyr.live.verifications.rejected").tag("kind", "verify").counter().count()).isEqualTo(1.0)

            release.complete(Unit)
            assertThat(running.await()).isEqualTo("verified")
            assertThat(queued.await()).isEqualTo("verified")
            assertThat(executions.get()).isEqualTo(2)
            assertThat(manager.active()).isEmpty()

            val admitted = manager.launch(request(), VerificationKind.Verify, blockingWork)
            assertThat(admitted.await()).isEqualTo("verified")
        }
    }
}
