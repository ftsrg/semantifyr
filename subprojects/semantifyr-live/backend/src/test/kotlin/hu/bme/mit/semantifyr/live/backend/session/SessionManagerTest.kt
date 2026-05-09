/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.session

import com.google.inject.Inject
import com.google.inject.Provider
import hu.bme.mit.semantifyr.live.backend.BackendConfig
import hu.bme.mit.semantifyr.live.backend.SessionManagerConfig
import hu.bme.mit.semantifyr.live.backend.server.WebSocketHandler
import hu.bme.mit.semantifyr.live.backend.testing.handler
import hu.bme.mit.semantifyr.live.backend.testing.installSemantifyrApp
import hu.bme.mit.semantifyr.live.backend.testing.testInjector
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.server.testing.testApplication
import io.ktor.server.websocket.WebSocketServerSession
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.kotlin.wheneverBlocking
import java.util.concurrent.atomic.AtomicReference
import io.ktor.client.plugins.websocket.WebSockets as ClientWebSockets

class SessionManagerTest {

    private fun createTestSetup(
        maxGlobal: Int = 4,
        onSession: suspend (WebSocketServerSession) -> Unit,
    ): Pair<WebSocketHandler, SessionManager> {
        val config = BackendConfig(
            sessionManager = SessionManagerConfig(maxSessionsGlobal = maxGlobal, maxSessionsPerIp = 2),
        )

        val capturedWs = AtomicReference<WebSocketServerSession>()
        val mockLspSession = mock<LspSession>()
        whenever(mockLspSession.sessionId).thenReturn("mock-session-${System.nanoTime()}")
        wheneverBlocking { mockLspSession.run() } doSuspendableAnswer {
            onSession(capturedWs.get())
        }

        val injector = testInjector(config) {
            bind(LspSession::class.java).toProvider(
                object : Provider<LspSession> {
                    @Inject
                    lateinit var contextProvider: Provider<SessionContext>

                    override fun get(): LspSession {
                        capturedWs.set(contextProvider.get().webSocketSession as WebSocketServerSession)
                        return mockLspSession
                    }
                },
            ).`in`(SessionScoped::class.java)
        }

        return injector.handler<WebSocketHandler>() to injector.handler<SessionManager>()
    }

    @Test
    fun `activeSessions starts at zero`() {
        val manager = testInjector().handler<SessionManager>()
        assertThat(manager.activeSessions).isEqualTo(0)
        assertThat(manager.maxSessions).isEqualTo(32)
    }

    @Test
    fun `session increments and decrements active count`() = testApplication {
        val sessionStarted = CompletableDeferred<Unit>()
        val sessionRelease = CompletableDeferred<Unit>()

        val (handler, manager) = createTestSetup { _ ->
            sessionStarted.complete(Unit)
            sessionRelease.await()
        }
        installSemantifyrApp(contentNegotiation = false, webSockets = true) {
            with(handler) {
                configure()
            }
        }

        val wsClient = createClient { install(ClientWebSockets) }
        wsClient.webSocket("/ws/lsp/oxsts") {
            sessionStarted.await()
            assertThat(manager.activeSessions).isEqualTo(1)
            sessionRelease.complete(Unit)
        }

        withTimeout(5_000) {
            while (manager.activeSessions != 0) {
                delay(10)
            }
        }
        assertThat(manager.activeSessions).isEqualTo(0)
    }

    @Test
    fun `global session limit is enforced`() = testApplication {
        val sessionRelease = CompletableDeferred<Unit>()

        val (handler, _) = createTestSetup(maxGlobal = 1) { _ ->
            sessionRelease.await()
        }
        installSemantifyrApp(contentNegotiation = false, webSockets = true) {
            with(handler) {
                configure()
            }
        }

        val wsClient = createClient { install(ClientWebSockets) }
        wsClient.webSocket("/ws/lsp/oxsts") {
            wsClient.webSocket("/ws/lsp/oxsts") {
                val reason = closeReason.await()
                assertThat(reason?.code).isEqualTo(4429.toShort())
            }
            sessionRelease.complete(Unit)
        }
    }
}
