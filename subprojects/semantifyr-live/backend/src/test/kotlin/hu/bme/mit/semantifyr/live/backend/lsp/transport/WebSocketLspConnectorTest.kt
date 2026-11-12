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
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.lsp4j.Hover
import org.eclipse.lsp4j.HoverParams
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest
import org.eclipse.lsp4j.jsonrpc.services.ServiceEndpoints
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.io.IOException
import java.util.concurrent.CompletableFuture
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
            val connector = WebSocketLspConnector(webSocketSession, languageServer, SimpleMeterRegistry())
            incoming.close(IOException("Ping timeout"))
            withTimeout(5.seconds) {
                connector.run()
            }
        }
    }

    @Test
    suspend fun `answered requests are timed by method and an unknown method counts as other`() {
        val incoming = Channel<Frame>(Channel.UNLIMITED)
        val webSocketSession = TestWebSocketSession(incoming)
        val languageServer = mock<SessionLanguageServer> {
            on { toEndpoint() } doReturn ServiceEndpoints.toEndpoint(HoverServer())
            on { supportedMethods() } doReturn ServiceEndpoints.getSupportedMethods(HoverServer::class.java)
        }
        val registry = SimpleMeterRegistry()
        val hover = """{"jsonrpc":"2.0","id":1,"method":"textDocument/hover","params":{"textDocument":{"uri":"file:///workspace/a.oxsts"},"position":{"line":0,"character":0}}}"""
        val unknown = """{"jsonrpc":"2.0","id":2,"method":"made/up","params":{}}"""

        coroutineScope {
            val connector = WebSocketLspConnector(webSocketSession, languageServer, registry)
            incoming.send(Frame.Text(hover))
            incoming.send(Frame.Text(unknown))
            incoming.close()
            withTimeout(5.seconds) {
                connector.run()
            }
        }

        val count = { method: String ->
            registry.get("semantifyr.live.language.requests").tag("method", method).timer().count()
        }
        assertThat(count("textDocument/hover")).isEqualTo(1)
        assertThat(count("other")).isEqualTo(1)
        assertThat(registry.find("semantifyr.live.language.requests").tag("method", "made/up").timer()).isNull()
    }

    @Test
    suspend fun `connector exits cleanly when the incoming channel is closed cleanly`() {
        val incoming = Channel<Frame>(Channel.UNLIMITED)
        val webSocketSession = TestWebSocketSession(incoming)
        val languageServer = buildLanguageServer()

        coroutineScope {
            val connector = WebSocketLspConnector(webSocketSession, languageServer, SimpleMeterRegistry())
            incoming.close()
            withTimeout(5.seconds) {
                connector.run()
            }
        }
    }
}

private class HoverServer {
    @JsonRequest("textDocument/hover")
    fun hover(params: HoverParams): CompletableFuture<Hover?> {
        return CompletableFuture.completedFuture(null)
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
