/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.session

import hu.bme.mit.semantifyr.live.backend.lsp.UriRewriter
import hu.bme.mit.semantifyr.live.backend.lsp.bridge.LspBridge
import hu.bme.mit.semantifyr.live.backend.lsp.bridge.LspClientRawConnector
import hu.bme.mit.semantifyr.live.backend.lsp.bridge.LspMessageInterceptor
import hu.bme.mit.semantifyr.live.backend.lsp.bridge.LspServerRawConnector
import hu.bme.mit.semantifyr.live.backend.utils.lspMessageHandler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.lsp4j.jsonrpc.messages.Message
import org.eclipse.lsp4j.jsonrpc.messages.NotificationMessage
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

class LspMessageProxyTest {

    private val rewriter = UriRewriter(
        clientUri = "file:///workspace/",
        serverUri = "file:///tmp/session/",
    )

    private class FakeClientRawConnector : LspClientRawConnector {
        private val incoming = Channel<String>(Channel.UNLIMITED)
        val sentToClient = Channel<String>(Channel.UNLIMITED)

        override suspend fun receiveFromClient(): String? = runCatching { incoming.receive() }.getOrNull()
        override suspend fun sendToClient(raw: String) {
            sentToClient.send(raw)
        }

        fun simulateClientSent(raw: String) {
            incoming.trySend(raw)
        }
        fun closeIncoming() {
            incoming.close()
        }
    }

    private class FakeServerRawConnector : LspServerRawConnector {
        private val incoming = Channel<String>(Channel.UNLIMITED)
        val sentToServer = Channel<String>(Channel.UNLIMITED)

        override suspend fun sendToServer(raw: String) {
            sentToServer.send(raw)
        }
        override suspend fun receiveFromServer(): String {
            val result = incoming.receiveCatching()
            return result.getOrNull() ?: throw CancellationException("server channel closed")
        }

        fun simulateServerSent(raw: String) {
            incoming.trySend(raw)
        }
    }

    private class RecordingInterceptor(
        private val consumeClient: Boolean = false,
        private val consumeServer: Boolean = false,
    ) : LspMessageInterceptor {
        val clientSeen = mutableListOf<String>()
        val serverSeen = mutableListOf<String>()

        override suspend fun handleClientMessage(
            raw: String,
            message: Message,
            bridge: LspBridge,
        ): Boolean {
            clientSeen += raw
            return !consumeClient
        }

        override suspend fun handleServerMessage(
            raw: String,
            message: Message,
            bridge: LspBridge,
        ): Boolean {
            serverSeen += raw
            return !consumeServer
        }
    }

    @Test
    fun `client message is passed to interceptors and then forwarded to the server with URI rewrite`() = runTest {
        val clientConnector = FakeClientRawConnector()
        val serverConnector = FakeServerRawConnector()
        val interceptor = RecordingInterceptor()
        val proxy = LspMessageProxy(serverConnector, clientConnector, rewriter, listOf(interceptor))

        val notification = notification(uri = "file:///workspace/snippet.oxsts")
        val raw = serialize(notification)

        withRunningProxy(proxy, clientConnector) {
            clientConnector.simulateClientSent(raw)
            val forwarded = withTimeout(1.seconds) { serverConnector.sentToServer.receive() }

            assertThat(forwarded).contains("file:///tmp/session/snippet.oxsts")
            assertThat(forwarded).doesNotContain("file:///workspace/")
            assertThat(interceptor.clientSeen).containsExactly(raw)
        }
    }

    @Test
    fun `server message is passed to interceptors and then forwarded to the client with URI rewrite`() = runTest {
        val clientConnector = FakeClientRawConnector()
        val serverConnector = FakeServerRawConnector()
        val interceptor = RecordingInterceptor()
        val proxy = LspMessageProxy(serverConnector, clientConnector, rewriter, listOf(interceptor))

        val serverMessage = serialize(notification(uri = "file:///tmp/session/snippet.oxsts"))

        withRunningProxy(proxy, clientConnector) {
            serverConnector.simulateServerSent(serverMessage)
            val forwarded = withTimeout(1.seconds) { clientConnector.sentToClient.receive() }

            assertThat(forwarded).contains("file:///workspace/snippet.oxsts")
            assertThat(forwarded).doesNotContain("file:///tmp/session/")
            assertThat(interceptor.serverSeen).singleElement().isEqualTo(forwarded)
        }
    }

    @Test
    fun `interceptor consuming a client message prevents forwarding to the server`() = runTest {
        val clientConnector = FakeClientRawConnector()
        val serverConnector = FakeServerRawConnector()
        val interceptor = RecordingInterceptor(consumeClient = true)
        val proxy = LspMessageProxy(serverConnector, clientConnector, rewriter, listOf(interceptor))

        val raw = serialize(notification(uri = "file:///workspace/snippet.oxsts"))

        withRunningProxy(proxy, clientConnector) {
            clientConnector.simulateClientSent(raw)
            waitUntil { interceptor.clientSeen.isNotEmpty() }

            assertThat(interceptor.clientSeen).containsExactly(raw)
            assertThat(serverConnector.sentToServer.tryReceive().getOrNull()).isNull()
        }
    }

    @Test
    fun `interceptor consuming a server message prevents forwarding to the client`() = runTest {
        val clientConnector = FakeClientRawConnector()
        val serverConnector = FakeServerRawConnector()
        val interceptor = RecordingInterceptor(consumeServer = true)
        val proxy = LspMessageProxy(serverConnector, clientConnector, rewriter, listOf(interceptor))

        val serverMessage = serialize(notification(uri = "file:///tmp/session/snippet.oxsts"))

        withRunningProxy(proxy, clientConnector) {
            serverConnector.simulateServerSent(serverMessage)
            waitUntil { interceptor.serverSeen.isNotEmpty() }

            assertThat(interceptor.serverSeen).hasSize(1)
            assertThat(clientConnector.sentToClient.tryReceive().getOrNull()).isNull()
        }
    }

    @Test
    fun `first interceptor to consume short-circuits the chain`() = runTest {
        val first = RecordingInterceptor()
        val second = RecordingInterceptor(consumeClient = true)
        val third = RecordingInterceptor()
        val clientConnector = FakeClientRawConnector()
        val serverConnector = FakeServerRawConnector()
        val proxy = LspMessageProxy(serverConnector, clientConnector, rewriter, listOf(first, second, third))

        val raw = serialize(notification(uri = "file:///workspace/snippet.oxsts"))

        withRunningProxy(proxy, clientConnector) {
            clientConnector.simulateClientSent(raw)
            waitUntil { second.clientSeen.isNotEmpty() }

            assertThat(first.clientSeen).containsExactly(raw)
            assertThat(second.clientSeen).containsExactly(raw)
            assertThat(third.clientSeen).isEmpty()
        }
    }

    private suspend fun waitUntil(condition: () -> Boolean) {
        withTimeout(1.seconds) {
            while (!condition()) {
                yield()
            }
        }
    }

    /**
     * Runs [proxy] in a launched job during [block], then closes the client connector
     * so the proxy can return cleanly.
     */
    private suspend fun withRunningProxy(
        proxy: LspMessageProxy,
        clientConnector: FakeClientRawConnector,
        block: suspend () -> Unit,
    ) = coroutineScope {
        val ready = CompletableDeferred<Unit>()
        val job = launch {
            ready.complete(Unit)
            proxy.run()
        }
        ready.await()
        try {
            block()
        } finally {
            clientConnector.closeIncoming()
            job.cancel()
        }
    }

    @Test
    fun `LspBridge sendToLspServer with Message serializes and forwards`() = runTest {
        val clientConnector = FakeClientRawConnector()
        val serverConnector = FakeServerRawConnector()
        val proxy = LspMessageProxy(serverConnector, clientConnector, rewriter, emptyList())

        val message = notification(uri = "file:///workspace/a.oxsts")
        proxy.sendToLspServer(message)

        val sent = serverConnector.sentToServer.receive()
        assertThat(sent).isEqualTo(serialize(message))
    }

    @Test
    fun `LspBridge sendToLspServer with raw string forwards verbatim`() = runTest {
        val clientConnector = FakeClientRawConnector()
        val serverConnector = FakeServerRawConnector()
        val proxy = LspMessageProxy(serverConnector, clientConnector, rewriter, emptyList())

        proxy.sendToLspServer("raw-payload")

        assertThat(serverConnector.sentToServer.receive()).isEqualTo("raw-payload")
    }

    @Test
    fun `LspBridge sendToLspClient with Message serializes and forwards`() = runTest {
        val clientConnector = FakeClientRawConnector()
        val serverConnector = FakeServerRawConnector()
        val proxy = LspMessageProxy(serverConnector, clientConnector, rewriter, emptyList())

        val message = notification(uri = "file:///workspace/a.oxsts")
        proxy.sendToLspClient(message)

        assertThat(clientConnector.sentToClient.receive()).isEqualTo(serialize(message))
    }

    @Test
    fun `LspBridge sendToLspClient with raw string forwards verbatim`() = runTest {
        val clientConnector = FakeClientRawConnector()
        val serverConnector = FakeServerRawConnector()
        val proxy = LspMessageProxy(serverConnector, clientConnector, rewriter, emptyList())

        proxy.sendToLspClient("raw-response")

        assertThat(clientConnector.sentToClient.receive()).isEqualTo("raw-response")
    }

    @Test
    fun `LspBridge recordError increments error count surfaced by getInfo`() {
        val clientConnector = FakeClientRawConnector()
        val serverConnector = FakeServerRawConnector()
        val proxy = LspMessageProxy(serverConnector, clientConnector, rewriter, emptyList())

        assertThat(proxy.getInfo().errorCount).isEqualTo(0L)
        proxy.recordError()
        proxy.recordError()
        assertThat(proxy.getInfo().errorCount).isEqualTo(2L)
    }

    private fun notification(uri: String) = NotificationMessage().apply {
        method = "textDocument/publishDiagnostics"
        params = mapOf("uri" to uri, "diagnostics" to emptyList<Any>())
    }

    private fun serialize(message: Message): String = lspMessageHandler.serialize(message)
}
