/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.server

import com.google.inject.AbstractModule
import com.google.inject.Guice
import hu.bme.mit.semantifyr.live.backend.BackendConfig
import hu.bme.mit.semantifyr.live.backend.BackendModule
import hu.bme.mit.semantifyr.live.backend.Flavor
import hu.bme.mit.semantifyr.live.backend.session.SessionLimitReachedException
import hu.bme.mit.semantifyr.live.backend.session.SessionManager
import io.ktor.client.plugins.websocket.WebSockets as ClientWebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.server.application.*
import io.ktor.server.testing.*
import io.ktor.server.websocket.WebSockets as ServerWebSockets
import io.ktor.server.websocket.WebSocketServerSession
import io.ktor.websocket.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.wheneverBlocking

class WebSocketHandlerTest {

    private fun createHandler(
        onRunSession: suspend (WebSocketServerSession, String, Flavor) -> Unit = { ws, _, _ ->
            ws.close(CloseReason(CloseReason.Codes.NORMAL, "done"))
        },
    ): WebSocketHandler {
        val sessionManager = mock<SessionManager>()
        wheneverBlocking { sessionManager.runSession(any(), any(), any()) }.thenAnswer { invocation ->
            val ws = invocation.getArgument<WebSocketServerSession>(0)
            val ip = invocation.getArgument<String>(1)
            val flavor = invocation.getArgument<Flavor>(2)
            kotlinx.coroutines.runBlocking { onRunSession(ws, ip, flavor) }
        }

        val config = BackendConfig()
        val injector = Guice.createInjector(BackendModule(config), object : AbstractModule() {
            override fun configure() {
                bind(SessionManager::class.java).toInstance(sessionManager)
            }
        })
        return injector.getInstance(WebSocketHandler::class.java)
    }

    private fun ApplicationTestBuilder.installHandler(handler: WebSocketHandler) {
        install(createApplicationPlugin("ws-setup") {
            application.install(ServerWebSockets)
            with(handler) { application.configure() }
        })
    }

    @Test
    fun `unknown flavor returns close code 4404`() = testApplication {
        installHandler(createHandler())

        val wsClient = createClient { install(ClientWebSockets) }
        wsClient.webSocket("/ws/lsp/nonexistent") {
            val reason = closeReason.await()
            assertThat(reason?.code).isEqualTo(4404.toShort())
            assertThat(reason?.message).contains("nonexistent")
        }
    }

    @Test
    fun `valid flavor connects and runs session`() = testApplication {
        var ranWithFlavor: String? = null
        val handler = createHandler { ws, _, flavor ->
            ranWithFlavor = flavor.id
            ws.close(CloseReason(CloseReason.Codes.NORMAL, "done"))
        }
        installHandler(handler)

        val wsClient = createClient { install(ClientWebSockets) }
        wsClient.webSocket("/ws/lsp/oxsts") {
            val reason = closeReason.await()
            assertThat(reason?.code).isEqualTo(CloseReason.Codes.NORMAL.code)
        }
        assertThat(ranWithFlavor).isEqualTo("oxsts")
    }

    @Test
    fun `session limit returns close code 4429`() = testApplication {
        val handler = createHandler { _, _, _ ->
            throw SessionLimitReachedException("Too many sessions")
        }
        installHandler(handler)

        val wsClient = createClient { install(ClientWebSockets) }
        wsClient.webSocket("/ws/lsp/oxsts") {
            val reason = closeReason.await()
            assertThat(reason?.code).isEqualTo(4429.toShort())
            assertThat(reason?.message).contains("Too many sessions")
        }
    }

    @Test
    fun `internal error returns close code 4500`() = testApplication {
        val handler = createHandler { _, _, _ ->
            throw RuntimeException("something broke")
        }
        installHandler(handler)

        val wsClient = createClient { install(ClientWebSockets) }
        wsClient.webSocket("/ws/lsp/oxsts") {
            val reason = closeReason.await()
            assertThat(reason?.code).isEqualTo(4500.toShort())
        }
    }
}
