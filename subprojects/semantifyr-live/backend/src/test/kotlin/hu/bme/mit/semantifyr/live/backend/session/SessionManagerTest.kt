/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.session

import com.google.inject.AbstractModule
import com.google.inject.Guice
import hu.bme.mit.semantifyr.live.backend.BackendConfig
import hu.bme.mit.semantifyr.live.backend.BackendModule
import hu.bme.mit.semantifyr.live.backend.SessionManagerConfig
import hu.bme.mit.semantifyr.live.backend.server.WebSocketHandler
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.server.application.*
import io.ktor.server.testing.*
import io.ktor.websocket.*
import kotlinx.coroutines.CompletableDeferred
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.kotlin.wheneverBlocking
import io.ktor.client.plugins.websocket.WebSockets as ClientWebSockets
import io.ktor.server.websocket.WebSockets as ServerWebSockets

class SessionManagerTest {

    private fun createTestSetup(
        maxGlobal: Int = 4,
        onSession: suspend (io.ktor.server.websocket.WebSocketServerSession) -> Unit,
    ): Pair<WebSocketHandler, SessionManager> {
        val config = BackendConfig(
            sessionManager = SessionManagerConfig(maxSessionsGlobal = maxGlobal, maxSessionsPerIp = 2),
        )

        val capturedWs = java.util.concurrent.atomic.AtomicReference<io.ktor.server.websocket.WebSocketServerSession>()
        val mockLspSession = mock<LspSession>()
        whenever(mockLspSession.sessionId).thenReturn("mock-session-${System.nanoTime()}")
        wheneverBlocking { mockLspSession.run() } doSuspendableAnswer {
            onSession(capturedWs.get())
        }

        val injector = Guice.createInjector(
            com.google.inject.util.Modules.override(BackendModule(config)).with(object : AbstractModule() {
                override fun configure() {
                    // Session-scoped override: capture the seeded SessionContext's WebSocketSession and return the mock LspSession.
                    bind(LspSession::class.java).toProvider(object : com.google.inject.Provider<LspSession> {
                        @com.google.inject.Inject
                        lateinit var contextProvider: com.google.inject.Provider<SessionContext>

                        override fun get(): LspSession {
                            capturedWs.set(contextProvider.get().webSocketSession as io.ktor.server.websocket.WebSocketServerSession)
                            return mockLspSession
                        }
                    }).`in`(SessionScoped::class.java)
                }
            }),
        )

        return injector.getInstance(WebSocketHandler::class.java) to
            injector.getInstance(SessionManager::class.java)
    }

    private fun ApplicationTestBuilder.installHandler(handler: WebSocketHandler) {
        install(
            createApplicationPlugin("ws-setup") {
                application.install(ServerWebSockets)
                with(handler) { application.configure() }
            },
        )
    }

    @Test
    fun `activeSessions starts at zero`() {
        val config = BackendConfig()
        val injector = Guice.createInjector(BackendModule(config))
        val manager = injector.getInstance(SessionManager::class.java)
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
        installHandler(handler)

        val wsClient = createClient { install(ClientWebSockets) }
        wsClient.webSocket("/ws/lsp/oxsts") {
            sessionStarted.await()
            assertThat(manager.activeSessions).isEqualTo(1)
            sessionRelease.complete(Unit)
        }
        // Server-side cleanup runs after the handler returns; wait with a timeout instead of a fixed delay.
        kotlinx.coroutines.withTimeout(5_000) {
            while (manager.activeSessions != 0) {
                kotlinx.coroutines.delay(10)
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
        installHandler(handler)

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
