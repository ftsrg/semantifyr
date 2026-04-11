/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.session

import com.google.inject.assistedinject.Assisted
import com.google.inject.assistedinject.AssistedInject
import hu.bme.mit.semantifyr.live.backend.BackendConfig
import hu.bme.mit.semantifyr.live.backend.Flavor
import hu.bme.mit.semantifyr.live.backend.WorkspaceLayout
import hu.bme.mit.semantifyr.live.backend.lsp.LspFrameCodec
import hu.bme.mit.semantifyr.live.backend.lsp.UriRewriter
import hu.bme.mit.semantifyr.live.backend.lsp.WorkspaceFileSyncer
import hu.bme.mit.semantifyr.live.backend.utils.MdcContext
import org.slf4j.MDC
import hu.bme.mit.semantifyr.live.backend.utils.debug
import hu.bme.mit.semantifyr.live.backend.utils.error
import hu.bme.mit.semantifyr.live.backend.utils.info
import hu.bme.mit.semantifyr.live.backend.utils.loggerFactory
import io.ktor.websocket.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.ExecuteCommandParams
import org.eclipse.lsp4j.jsonrpc.json.JsonRpcMethod
import org.eclipse.lsp4j.jsonrpc.json.MessageJsonHandler
import org.eclipse.lsp4j.jsonrpc.messages.NotificationMessage
import org.eclipse.lsp4j.jsonrpc.messages.RequestMessage
import org.eclipse.lsp4j.jsonrpc.messages.ResponseMessage
import java.io.InputStream
import java.io.OutputStream
import java.util.*
import kotlin.coroutines.cancellation.CancellationException
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

private val lspMessageHandler = MessageJsonHandler(
    mapOf(
        "textDocument/didOpen" to JsonRpcMethod.notification(
            "textDocument/didOpen", DidOpenTextDocumentParams::class.java,
        ),
        "textDocument/didChange" to JsonRpcMethod.notification(
            "textDocument/didChange", DidChangeTextDocumentParams::class.java,
        ),
        "workspace/executeCommand" to JsonRpcMethod.request(
            "workspace/executeCommand", Any::class.java, ExecuteCommandParams::class.java,
        ),
    ),
)

class LiveSession @AssistedInject constructor(
    @param:Assisted val flavor: Flavor,
    @param:Assisted val sessionId: String,
    val config: BackendConfig,
    val lspRunner: LspServerRunner,
    val verificationGate: VerificationGate,
) : AutoCloseable {

    private val logger by loggerFactory()
    private lateinit var runScope: CoroutineScope

    private val workingDirectoryPath = config.sessionManager.rootWorkPath.resolve("sessions").resolve(sessionId)
    private val clientUri = "file:///workspace/${flavor.fileName}"
    private val serverUri = workingDirectoryPath.resolve(flavor.fileName).toUri().toString()
    private val workspaceFileSyncer = WorkspaceFileSyncer(
        sessionId = sessionId,
        clientUri = clientUri,
        targetFile = workingDirectoryPath.resolve(flavor.fileName),
        verifyCommand = flavor.verifyCommand,
    )
    private val uriRewriter = UriRewriter(
        clientUri = clientUri,
        serverUri = serverUri,
    )

    suspend fun run(webSocketSession: WebSocketSession) = withContext(MdcContext("sessionId" to sessionId)) {
        logger.info { "Starting (flavor=${flavor.id}, workspace=$workingDirectoryPath)" }
        logger.debug { "ClientUri=$clientUri serverUri=$serverUri" }

        workingDirectoryPath.createDirectories()

        when (flavor.workspaceLayout) {
            WorkspaceLayout.SingleFile -> {
                workingDirectoryPath.resolve(flavor.fileName).writeText("")
            }
        }

        runScope = CoroutineScope(currentCoroutineContext() + Dispatchers.Default.limitedParallelism(1))

        lspRunner.runLspWith(flavor, workingDirectoryPath, runScope) { stdin, stdout ->
            logger.info { "Launching message loops" }

            val verificationManager = SessionVerificationManager(
                gate = verificationGate,
                scope = runScope,
                sendCancelToLsp = { requestId -> sendCancelToLsp(stdin, requestId) },
                sendErrorToClient = { response -> webSocketSession.sendResponseToClient(response) },
            )

            val serverJob = runScope.launch {
                webSocketSession.forwardServerMessages(stdout, verificationManager)
            }
            val clientJob = runScope.launch {
                webSocketSession.forwardClientMessages(stdin, verificationManager)
            }

            serverJob.invokeOnCompletion {
                if (it == null) {
                    logger.info { "LSP server job stopped, canceling scope" }
                    runScope.cancel()
                }
            }
            clientJob.invokeOnCompletion {
                if (it == null) {
                    logger.info { "LSP client job stopped, canceling scope" }
                    runScope.cancel()
                }
            }

            serverJob.join()
            clientJob.join()

            logger.info { "LSP bridge ended, closing connection" }
        }
    }

    private suspend fun WebSocketSession.forwardServerMessages(
        stdout: InputStream,
        verificationManager: SessionVerificationManager,
    ) {
        logger.debug { "Server message loop started" }
        while (true) {
            val raw = runInterruptible(Dispatchers.IO) {
                LspFrameCodec.readFrame(stdout)
            }

            if (raw == null) {
                logger.info { "LSP server stdout EOF" }
                break
            }

            val parsed = lspMessageHandler.parseMessage(raw)
            logger.debug { "LSP server -> client: $parsed" }

            val isConsumed = verificationManager.handleServerMessage(parsed)
            if (isConsumed) {
                continue
            }

            val rewritten = uriRewriter.serverToClient(raw)
            send(Frame.Text(rewritten))
        }

        close(CloseReason(CloseReason.Codes.GOING_AWAY, "LSP process exited"))
    }

    private suspend fun WebSocketSession.forwardClientMessages(
        stdin: OutputStream,
        verificationManager: SessionVerificationManager,
    ) {
        logger.debug { "Client message loop started" }
        for (frame in incoming) {
            if (frame is Frame.Text) {
                handleClientMessage(frame.readText(), stdin, verificationManager)
            }
        }
        logger.info { "WebSocket incoming channel closed (client disconnected)" }
    }

    private suspend fun handleClientMessage(
        raw: String,
        stdin: OutputStream,
        verificationManager: SessionVerificationManager,
    ) {
        val parsed = lspMessageHandler.parseMessage(raw)
        logger.debug { "LSP client -> server: $parsed" }

        workspaceFileSyncer.handleOutgoingMessage(parsed)

        val isConsumed = verificationManager.handleClientMessage(parsed)
        if (isConsumed) {
            logger.debug { "Message consumed by verification manager" }
            return
        }

        val rewritten = uriRewriter.clientToServer(raw)

        runInterruptible(Dispatchers.IO) {
            LspFrameCodec.writeFrame(stdin, rewritten)
        }
    }

    private suspend fun sendCancelToLsp(stdin: OutputStream, requestId: String) {
        logger.info { "Sending \$/cancelRequest to LSP server (requestId=$requestId)" }
        val cancelNotification = NotificationMessage().apply {
            method = "\$/cancelRequest"
            params = mapOf("id" to requestId)
        }
        val serialized = lspMessageHandler.serialize(cancelNotification)
        runInterruptible(Dispatchers.IO) {
            LspFrameCodec.writeFrame(stdin, serialized)
        }
    }

    private suspend fun WebSocketSession.sendResponseToClient(response: ResponseMessage) {
        logger.debug { "Sending error response to LSP client (requestId=${response.id})" }
        val serialized = lspMessageHandler.serialize(response)
        send(Frame.Text(serialized))
    }

    override fun close() {
        MDC.put("sessionId", sessionId)
        try {
            logger.info { "Closing session" }

            if (this::runScope.isInitialized) {
                runScope.cancel(CancellationException("session closed"))
            }

            try {
                workingDirectoryPath.toFile().deleteRecursively()
                logger.debug { "workspace deleted" }
            } catch (t: Throwable) {
                logger.error { "Failed to delete workspace $workingDirectoryPath: $t" }
            }
        } finally {
            MDC.remove("sessionId")
        }
    }

    interface Factory {
        fun create(flavor: Flavor, sessionId: String): LiveSession
    }
}
