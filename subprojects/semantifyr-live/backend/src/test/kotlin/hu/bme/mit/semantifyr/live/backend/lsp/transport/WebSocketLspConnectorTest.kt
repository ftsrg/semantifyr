/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.lsp.transport

import hu.bme.mit.semantifyr.live.backend.lsp.service.SessionLanguageServer
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketExtension
import io.ktor.websocket.WebSocketSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.lsp4j.jsonrpc.services.ServiceEndpoints
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.io.IOException
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.seconds

class WebSocketLspConnectorTest {

    private fun buildLanguageServer(): SessionLanguageServer {
        val emptyEndpoint = ServiceEndpoints.toEndpoint(object {})
        return mock {
            on { toEndpoint() } doReturn emptyEndpoint
            on { supportedMethods() } doReturn emptyMap()
        }
    }

    @Test
    suspend fun `connector exits cleanly when the incoming channel closes with IOException`() {
        val incoming = Channel<Frame>(Channel.UNLIMITED)
        val webSocketSession = TestWebSocketSession(incoming)
        val languageServer = buildLanguageServer()

        coroutineScope {
            val connector = WebSocketLspConnector(webSocketSession, languageServer, coroutineContext)
            incoming.close(IOException("Ping timeout"))
            withTimeout(5.seconds) {
                connector.run()
            }
            assertThat(connector.getInfo()).isNotNull
        }
    }

    @Test
    suspend fun `connector exits cleanly when the incoming channel is closed cleanly`() {
        val incoming = Channel<Frame>(Channel.UNLIMITED)
        val webSocketSession = TestWebSocketSession(incoming)
        val languageServer = buildLanguageServer()

        coroutineScope {
            val connector = WebSocketLspConnector(webSocketSession, languageServer, coroutineContext)
            incoming.close()
            withTimeout(5.seconds) {
                connector.run()
            }
            assertThat(connector.getInfo()).isNotNull
        }
    }
}

private class TestWebSocketSession(
    override val incoming: ReceiveChannel<Frame>,
) : WebSocketSession,
    CoroutineScope {

    private val sessionJob = Job()

    override val coroutineContext: CoroutineContext = sessionJob + Dispatchers.Default
    override val outgoing: SendChannel<Frame> = Channel(Channel.UNLIMITED)
    override var masking: Boolean = false
    override var maxFrameSize: Long = Long.MAX_VALUE
    override val extensions: List<WebSocketExtension<*>> = emptyList()

    override suspend fun flush() {
    }

    override fun terminate() {
        sessionJob.cancel()
    }
}
