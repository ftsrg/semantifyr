/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.session

import com.google.inject.Inject
import hu.bme.mit.semantifyr.live.backend.lsp.UriRewriter
import hu.bme.mit.semantifyr.live.backend.lsp.bridge.ArtifactsConfigInterceptor
import hu.bme.mit.semantifyr.live.backend.lsp.bridge.LspBridge
import hu.bme.mit.semantifyr.live.backend.lsp.bridge.LspClientRawConnector
import hu.bme.mit.semantifyr.live.backend.lsp.bridge.LspMessageInterceptor
import hu.bme.mit.semantifyr.live.backend.lsp.bridge.LspServerRawConnector
import hu.bme.mit.semantifyr.live.backend.lsp.bridge.LspServerReadinessInterceptor
import hu.bme.mit.semantifyr.live.backend.lsp.bridge.SemantifyrLiveMethodInterceptor
import hu.bme.mit.semantifyr.live.backend.lsp.bridge.VerificationMessageInterceptor
import hu.bme.mit.semantifyr.live.backend.lsp.bridge.WorkspaceSyncerInterceptor
import hu.bme.mit.semantifyr.live.backend.server.LspProxyInfo
import hu.bme.mit.semantifyr.live.backend.utils.lspMessageHandler
import hu.bme.mit.semantifyr.logging.debug
import hu.bme.mit.semantifyr.logging.error
import hu.bme.mit.semantifyr.logging.info
import hu.bme.mit.semantifyr.logging.loggerFactory
import hu.bme.mit.semantifyr.logging.trace
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.eclipse.lsp4j.jsonrpc.messages.Message
import org.eclipse.lsp4j.jsonrpc.messages.NotificationMessage
import org.eclipse.lsp4j.jsonrpc.messages.RequestMessage
import kotlin.time.TimeSource

@SessionScoped
class LspMessageProxy @Inject constructor(
    private val lspServerRawConnector: LspServerRawConnector,
    private val lspClientRawConnector: LspClientRawConnector,
    private val uriRewriter: UriRewriter,
    private val lspMessageInterceptors: @JvmSuppressWildcards List<LspMessageInterceptor>,
) : LspBridge {

    private val logger by loggerFactory()
    private val startMark = TimeSource.Monotonic.markNow()

    private val lspMessageInterceptors = listOf(
        lspServerReadinessInterceptor,
        workspaceSyncerInterceptor,
        artifactsConfigInterceptor,
        semantifyrLiveMethodInterceptor,
        verificationMessageInterceptor,
    )

    private var lastClientMessageMark = startMark
    private var lastServerMessageMark = startMark
    private var clientMessageCount = 0L
    private var serverMessageCount = 0L
    private var errorCount = 0L

    fun getInfo(): LspProxyInfo {
        return LspProxyInfo(
            clientMessageCount = clientMessageCount,
            serverMessageCount = serverMessageCount,
            errorCount = errorCount,
            timeSinceLastClientMessage = lastClientMessageMark.elapsedNow(),
            timeSinceLastServerMessage = lastServerMessageMark.elapsedNow(),
        )
    }

    suspend fun run() = coroutineScope {
        logger.info { "Launching message jobs" }

        val serverJob = launch { serverToClient() }
        val clientJob = launch { clientToServer() }

        serverJob.invokeOnCompletion {
            logger.info { "Server -> client message job stopped, cancelling session scope" }
            cancel()
        }
        clientJob.invokeOnCompletion {
            logger.info { "Client -> server message job stopped, cancelling session scope" }
            cancel()
        }

        serverJob.join()
        clientJob.join()

        logger.info {
            "Message jobs ended (clientMessages=$clientMessageCount, serverMessages=$serverMessageCount, errors=$errorCount)"
        }
    }

    override suspend fun sendToLspServer(message: Message) {
        sendToLspServer(lspMessageHandler.serialize(message))
    }

    override suspend fun sendToLspServer(raw: String) {
        lspServerRawConnector.sendToServer(raw)
    }

    override suspend fun sendToLspClient(message: Message) {
        sendToLspClient(lspMessageHandler.serialize(message))
    }

    override suspend fun sendToLspClient(raw: String) {
        lspClientRawConnector.sendToClient(raw)
    }

    override fun recordError() {
        errorCount++
    }

    private suspend fun clientToServer() {
        logger.info { "Client -> server message job started" }
        while (true) {
            val raw = lspClientRawConnector.receiveFromClient() ?: break

            clientMessageCount++
            lastClientMessageMark = TimeSource.Monotonic.markNow()
            logger.trace { "Client -> server raw: $raw" }

            val message = try {
                lspMessageHandler.parseMessage(raw)
            } catch (e: Exception) {
                // Log the raw payload before rethrowing so a malformed client message gets a
                // chance to show its body in the operator log instead of just the parse error.
                logger.error { "Failed to parse client message ($e): $raw" }
                throw e
            }
            val consumed = runInterceptors {
                it.interceptClientMessage(raw, message, this)
            }
            if (consumed) {
                logger.debug { "Client message consumed by interceptor (method=${message.methodOrNull()})" }
                continue
            }

            val rewritten = uriRewriter.clientToServer(raw)
            logger.trace { "Forwarding to LSP: $rewritten" }
            sendToLspServer(rewritten)
        }
        logger.info { "Client channel closed" }
    }

    private suspend fun serverToClient() {
        logger.info { "Server -> client message job started" }

        while (true) {
            val rawFromServer = try {
                lspServerRawConnector.receiveFromServer()
            } catch (_: CancellationException) {
                logger.info { "LSP server channel closed" }
                return
            }

            if (rawFromServer == null) {
                return
            }

            serverMessageCount++
            lastServerMessageMark = TimeSource.Monotonic.markNow()

            val raw = uriRewriter.serverToClient(rawFromServer)
            logger.trace { "Server -> client raw: $raw" }
            val message = lspMessageHandler.parseMessage(raw)

            val consumed = runInterceptors {
                it.interceptServerMessage(raw, message, this)
            }
            if (consumed) {
                logger.debug { "Server message consumed by interceptor (method=${message.methodOrNull()})" }
                continue
            }

            sendToLspClient(raw)
        }
    }

    private inline fun runInterceptors(block: (LspMessageInterceptor) -> Boolean): Boolean {
        for (interceptor in lspMessageInterceptors) {
            if (block(interceptor)) {
                return true
            }
        }
        return false
    }
}

private fun Message.methodOrNull(): String? = when (this) {
    is RequestMessage -> method
    is NotificationMessage -> method
    else -> null
}
