/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.server

import hu.bme.mit.semantifyr.live.backend.BackendConfig
import hu.bme.mit.semantifyr.live.backend.Flavor
import hu.bme.mit.semantifyr.live.backend.ServerConfig
import hu.bme.mit.semantifyr.live.backend.exceptions.SessionLimitReachedException
import hu.bme.mit.semantifyr.live.backend.lsp.session.SessionManager
import hu.bme.mit.semantifyr.live.backend.testing.handler
import hu.bme.mit.semantifyr.live.backend.testing.installSemantifyrApp
import hu.bme.mit.semantifyr.live.backend.testing.testInjector
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.server.testing.testApplication
import io.ktor.server.websocket.WebSocketServerSession
import io.ktor.websocket.CloseReason
import io.ktor.websocket.close
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.wheneverBlocking
import io.ktor.client.plugins.websocket.WebSockets as ClientWebSockets

class WebSocketHandlerTest {

    private fun createHandler(
        config: BackendConfig = BackendConfig(),
        onRunSession: suspend (WebSocketServerSession, String, Flavor) -> Unit = { ws, _, _ ->
            ws.close(CloseReason(CloseReason.Codes.NORMAL, "done"))
        },
    ): WebSocketHandler {
        val sessionManager = mock<SessionManager>()
        wheneverBlocking {
            sessionManager.runSession(any(), any(), any())
        } doSuspendableAnswer {
            val ws = it.getArgument<WebSocketServerSession>(0)
            val ip = it.getArgument<String>(1)
            val flavor = it.getArgument<Flavor>(2)
            onRunSession(ws, ip, flavor)
        }

        return testInjector(config) {
            bind(SessionManager::class.java).toInstance(sessionManager)
        }.handler<WebSocketHandler>()
    }

    @Test
    fun `unknown flavor returns close code 4404`() = testApplication {
        val handler = createHandler()
        installSemantifyrApp(contentNegotiation = false, webSockets = true) {
            with(handler) {
                configure()
            }
        }

        val wsClient = createClient {
            install(ClientWebSockets)
        }
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
        installSemantifyrApp(contentNegotiation = false, webSockets = true) {
            with(handler) {
                configure()
            }
        }

        val wsClient = createClient {
            install(ClientWebSockets)
        }
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
        installSemantifyrApp(contentNegotiation = false, webSockets = true) {
            with(handler) {
                configure()
            }
        }

        val wsClient = createClient {
            install(ClientWebSockets)
        }
        wsClient.webSocket("/ws/lsp/oxsts") {
            val reason = closeReason.await()
            assertThat(reason?.code).isEqualTo(4429.toShort())
            assertThat(reason?.message).contains("Too many sessions")
        }
    }

    @Test
    fun `exceeding handshake rate limit rejects second handshake`() = testApplication {
        val config = BackendConfig(server = ServerConfig(wsHandshakesPerPeriod = 1))
        val handler = createHandler(config)
        installSemantifyrApp(contentNegotiation = false, webSockets = true) {
            with(handler) {
                configure()
            }
        }

        val client = createClient {
            install(ClientWebSockets)
        }
        client.webSocket("/ws/lsp/oxsts") {
            closeReason.await()
        }
        val secondAttempt = runCatching {
            client.webSocket("/ws/lsp/oxsts") {
                closeReason.await()
            }
        }
        assertThat(secondAttempt.exceptionOrNull()).isNotNull
    }

    @Test
    fun `internal error returns close code 4500`() = testApplication {
        val handler = createHandler { _, _, _ ->
            throw RuntimeException("something broke")
        }
        installSemantifyrApp(contentNegotiation = false, webSockets = true) {
            with(handler) {
                configure()
            }
        }

        val wsClient = createClient {
            install(ClientWebSockets)
        }
        wsClient.webSocket("/ws/lsp/oxsts") {
            val reason = closeReason.await()
            assertThat(reason?.code).isEqualTo(4500.toShort())
        }
    }
}
