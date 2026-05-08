/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.session

import hu.bme.mit.semantifyr.live.backend.lsp.UriRewriter
import hu.bme.mit.semantifyr.live.backend.lsp.bridge.LspClientRawConnector
import hu.bme.mit.semantifyr.live.backend.lsp.bridge.LspMessageInterceptor
import hu.bme.mit.semantifyr.live.backend.lsp.bridge.LspServerRawConnector
import hu.bme.mit.semantifyr.live.backend.testing.FakeLspClientRawConnector
import hu.bme.mit.semantifyr.live.backend.testing.FakeLspServerRawConnector
import hu.bme.mit.semantifyr.live.backend.testing.LspMessages
import hu.bme.mit.semantifyr.live.backend.testing.RecordingLspInterceptor
import hu.bme.mit.semantifyr.live.backend.testing.serialize
import hu.bme.mit.semantifyr.live.backend.testing.testLspMessageHandler
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

class LspMessageProxyTest {

    private val rewriter = UriRewriter(
        clientUri = "file:///workspace/",
        serverUri = "file:///tmp/session/",
    )

    private fun newProxy(
        clientConnector: LspClientRawConnector,
        serverConnector: LspServerRawConnector,
        interceptors: List<LspMessageInterceptor> = emptyList(),
    ): LspMessageProxy {
        return LspMessageProxy(serverConnector, clientConnector, rewriter, testLspMessageHandler, interceptors)
    }

    @Test
    suspend fun `client message is passed to interceptors and then forwarded to the server with URI rewrite`() {
        val clientConnector = FakeLspClientRawConnector()
        val serverConnector = FakeLspServerRawConnector()
        val interceptor = RecordingLspInterceptor()
        val proxy = newProxy(clientConnector, serverConnector, listOf(interceptor))

        val raw = LspMessages.publishDiagnostics(uri = "file:///workspace/snippet.oxsts").serialize()

        withRunningProxy(proxy, clientConnector) {
            clientConnector.simulateClientSent(raw)
            val forwarded = withTimeout(1.seconds) {
                serverConnector.sentToServer.receive()
            }

            assertThat(forwarded).contains("file:///tmp/session/snippet.oxsts")
            assertThat(forwarded).doesNotContain("file:///workspace/")
            assertThat(interceptor.clientSeen).containsExactly(raw)
        }
    }

    @Test
    suspend fun `server message is passed to interceptors and then forwarded to the client with URI rewrite`() {
        val clientConnector = FakeLspClientRawConnector()
        val serverConnector = FakeLspServerRawConnector()
        val interceptor = RecordingLspInterceptor()
        val proxy = newProxy(clientConnector, serverConnector, listOf(interceptor))

        val serverMessage = LspMessages.publishDiagnostics(uri = "file:///tmp/session/snippet.oxsts").serialize()

        withRunningProxy(proxy, clientConnector) {
            serverConnector.simulateServerSent(serverMessage)
            val forwarded = withTimeout(1.seconds) {
                clientConnector.sentToClient.receive()
            }

            assertThat(forwarded).contains("file:///workspace/snippet.oxsts")
            assertThat(forwarded).doesNotContain("file:///tmp/session/")
            assertThat(interceptor.serverSeen).singleElement().isEqualTo(forwarded)
        }
    }

    @Test
    suspend fun `interceptor consuming a client message prevents forwarding to the server`() {
        val clientConnector = FakeLspClientRawConnector()
        val serverConnector = FakeLspServerRawConnector()
        val interceptor = RecordingLspInterceptor(consumeClient = true)
        val proxy = newProxy(clientConnector, serverConnector, listOf(interceptor))

        val raw = LspMessages.publishDiagnostics(uri = "file:///workspace/snippet.oxsts").serialize()

        withRunningProxy(proxy, clientConnector) {
            clientConnector.simulateClientSent(raw)
            waitUntil { interceptor.clientSeen.isNotEmpty() }

            assertThat(interceptor.clientSeen).containsExactly(raw)
            assertThat(serverConnector.sentToServer.tryReceive().getOrNull()).isNull()
        }
    }

    @Test
    suspend fun `interceptor consuming a server message prevents forwarding to the client`() {
        val clientConnector = FakeLspClientRawConnector()
        val serverConnector = FakeLspServerRawConnector()
        val interceptor = RecordingLspInterceptor(consumeServer = true)
        val proxy = newProxy(clientConnector, serverConnector, listOf(interceptor))

        val serverMessage = LspMessages.publishDiagnostics(uri = "file:///tmp/session/snippet.oxsts").serialize()

        withRunningProxy(proxy, clientConnector) {
            serverConnector.simulateServerSent(serverMessage)
            waitUntil { interceptor.serverSeen.isNotEmpty() }

            assertThat(interceptor.serverSeen).hasSize(1)
            assertThat(clientConnector.sentToClient.tryReceive().getOrNull()).isNull()
        }
    }

    @Test
    suspend fun `first interceptor to consume short-circuits the chain`() {
        val first = RecordingLspInterceptor()
        val second = RecordingLspInterceptor(consumeClient = true)
        val third = RecordingLspInterceptor()
        val clientConnector = FakeLspClientRawConnector()
        val serverConnector = FakeLspServerRawConnector()
        val proxy = newProxy(clientConnector, serverConnector, listOf(first, second, third))

        val raw = LspMessages.publishDiagnostics(uri = "file:///workspace/snippet.oxsts").serialize()

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
        clientConnector: FakeLspClientRawConnector,
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
    suspend fun `LspBridge sendToLspServer with Message serializes and forwards`() {
        val clientConnector = FakeLspClientRawConnector()
        val serverConnector = FakeLspServerRawConnector()
        val proxy = newProxy(clientConnector, serverConnector)

        val message = LspMessages.publishDiagnostics(uri = "file:///workspace/a.oxsts")
        proxy.sendToLspServer(message)

        val sent = serverConnector.sentToServer.receive()
        assertThat(sent).isEqualTo(message.serialize())
    }

    @Test
    suspend fun `LspBridge sendToLspServer with raw string forwards verbatim`() {
        val clientConnector = FakeLspClientRawConnector()
        val serverConnector = FakeLspServerRawConnector()
        val proxy = newProxy(clientConnector, serverConnector)

        proxy.sendToLspServer("raw-payload")

        assertThat(serverConnector.sentToServer.receive()).isEqualTo("raw-payload")
    }

    @Test
    suspend fun `LspBridge sendToLspClient with Message serializes and forwards`() {
        val clientConnector = FakeLspClientRawConnector()
        val serverConnector = FakeLspServerRawConnector()
        val proxy = newProxy(clientConnector, serverConnector)

        val message = LspMessages.publishDiagnostics(uri = "file:///workspace/a.oxsts")
        proxy.sendToLspClient(message)

        assertThat(clientConnector.sentToClient.receive()).isEqualTo(message.serialize())
    }

    @Test
    suspend fun `LspBridge sendToLspClient with raw string forwards verbatim`() {
        val clientConnector = FakeLspClientRawConnector()
        val serverConnector = FakeLspServerRawConnector()
        val proxy = newProxy(clientConnector, serverConnector)

        proxy.sendToLspClient("raw-response")

        assertThat(clientConnector.sentToClient.receive()).isEqualTo("raw-response")
    }

    @Test
    fun `LspBridge recordError increments error count surfaced by getInfo`() {
        val clientConnector = FakeLspClientRawConnector()
        val serverConnector = FakeLspServerRawConnector()
        val proxy = newProxy(clientConnector, serverConnector)

        assertThat(proxy.getInfo().errorCount).isEqualTo(0L)
        proxy.recordError()
        proxy.recordError()
        assertThat(proxy.getInfo().errorCount).isEqualTo(2L)
    }
}
