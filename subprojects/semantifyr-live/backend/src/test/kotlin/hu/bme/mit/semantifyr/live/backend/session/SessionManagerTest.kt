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
import io.ktor.client.plugins.websocket.WebSockets as ClientWebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.server.application.*
import io.ktor.server.testing.*
import io.ktor.server.websocket.WebSockets as ServerWebSockets
import io.ktor.websocket.*
import kotlinx.coroutines.CompletableDeferred
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.kotlin.wheneverBlocking

class SessionManagerTest {

    private fun createTestSetup(
        maxGlobal: Int = 4,
        onSession: suspend (io.ktor.server.websocket.WebSocketServerSession) -> Unit,
    ): Pair<WebSocketHandler, SessionManager> {
        val config = BackendConfig(
            sessionManager = SessionManagerConfig(maxSessionsGlobal = maxGlobal, maxSessionsPerIp = 2),
        )

        val mockLiveSession = mock<LiveSession>()
        whenever(mockLiveSession.sessionId).thenReturn("mock-session-${System.nanoTime()}")
        wheneverBlocking { mockLiveSession.run(any()) }.thenAnswer { invocation ->
            val ws = invocation.getArgument<io.ktor.server.websocket.WebSocketServerSession>(0)
            kotlinx.coroutines.runBlocking { onSession(ws) }
        }

        val factory = mock<LiveSession.Factory>()
        whenever(factory.create(any(), any())).thenReturn(mockLiveSession)

        val injector = Guice.createInjector(com.google.inject.util.Modules.override(BackendModule(config)).with(object : AbstractModule() {
            override fun configure() {
                bind(LiveSession.Factory::class.java).toInstance(factory)
            }
        }))

        return injector.getInstance(WebSocketHandler::class.java) to
            injector.getInstance(SessionManager::class.java)
    }

    private fun ApplicationTestBuilder.installHandler(handler: WebSocketHandler) {
        install(createApplicationPlugin("ws-setup") {
            application.install(ServerWebSockets)
            with(handler) { application.configure() }
        })
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
        // Allow server-side cleanup to complete
        kotlinx.coroutines.delay(100)
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
