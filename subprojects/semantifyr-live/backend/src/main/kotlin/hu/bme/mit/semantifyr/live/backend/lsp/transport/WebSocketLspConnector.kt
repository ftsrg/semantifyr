/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.lsp.transport

import hu.bme.mit.semantifyr.live.backend.lsp.service.SessionLanguageClient
import hu.bme.mit.semantifyr.live.backend.lsp.service.SessionLanguageServer
import hu.bme.mit.semantifyr.logging.error
import hu.bme.mit.semantifyr.logging.info
import hu.bme.mit.semantifyr.logging.loggerFactory
import hu.bme.mit.semantifyr.logging.warn
import io.ktor.websocket.*
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.eclipse.lsp4j.jsonrpc.MessageConsumer
import org.eclipse.lsp4j.jsonrpc.RemoteEndpoint
import org.eclipse.lsp4j.jsonrpc.messages.RequestMessage
import org.eclipse.lsp4j.jsonrpc.messages.ResponseMessage
import org.eclipse.lsp4j.jsonrpc.services.ServiceEndpoints
import java.io.IOException
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.TimeSource
import kotlin.time.toJavaDuration

private val requestDurationBuckets = arrayOf(
    Duration.ofMillis(10),
    Duration.ofMillis(50),
    Duration.ofMillis(100),
    Duration.ofMillis(250),
    Duration.ofMillis(500),
    Duration.ofSeconds(1),
    Duration.ofSeconds(2),
    Duration.ofSeconds(5),
    Duration.ofSeconds(10),
)

class WebSocketLspConnector(
    private val webSocketSession: WebSocketSession,
    private val sessionLanguageServer: SessionLanguageServer,
    private val registry: MeterRegistry,
) {

    private val logger by loggerFactory()

    private val outgoing = Channel<String>(capacity = OUTGOING_BUFFER)

    private val supportedMethods = buildMap {
        putAll(ServiceEndpoints.getSupportedMethods(SessionLanguageClient::class.java))
        putAll(sessionLanguageServer.supportedMethods())
    }

    private val jsonHandler = createLspMessageJsonHandler(supportedMethods)

    private class PendingRequest(
        val method: String,
        val receivedAt: TimeSource.Monotonic.ValueTimeMark,
    )

    private val pendingRequests = ConcurrentHashMap<String, PendingRequest>()

    private val remoteEndpoint = RemoteEndpoint(
        outboundConsumer(),
        sessionLanguageServer.toEndpoint(),
    ).also {
        jsonHandler.methodProvider = it
    }

    private val proxyClient = ServiceEndpoints.toServiceObject(remoteEndpoint, SessionLanguageClient::class.java)

    init {
        sessionLanguageServer.connect(proxyClient)
    }

    suspend fun run() = coroutineScope {
        logger.info { "LSP streaming started" }

        val writerJob = launch {
            writeOutgoing()
        }
        val readerJob = launch {
            readIncoming()
        }

        readerJob.invokeOnCompletion {
            logger.info { "Client -> server reader stopped, cancelling writer" }
            writerJob.cancel()
        }
        writerJob.invokeOnCompletion {
            logger.info { "Server -> client writer stopped, cancelling reader" }
            readerJob.cancel()
        }

        readerJob.join()
        writerJob.join()

        logger.info { "LSP streaming ended" }
    }

    private suspend fun readIncoming() {
        try {
            while (true) {
                val frame = webSocketSession.incoming.receive()
                if (frame !is Frame.Text) {
                    continue
                }
                val raw = frame.readText()
                val message = jsonHandler.parseMessage(raw)
                if (message is RequestMessage) {
                    // the method is client-supplied, so only the server's own methods become tag values
                    val method = message.method.takeIf {
                        it in supportedMethods
                    } ?: "other"
                    pendingRequests[message.id] = PendingRequest(method, TimeSource.Monotonic.markNow())
                }
                remoteEndpoint.consume(message)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: ClosedReceiveChannelException) {
            logger.info(e) { "Client closed the WebSocket" }
        } catch (e: IOException) {
            logger.info(e) { "Client -> server reader stopped" }
        } catch (e: Throwable) {
            logger.error(e) { "Client -> server reader failed" }
            throw e
        }
    }

    private suspend fun writeOutgoing() {
        for (json in outgoing) {
            try {
                webSocketSession.send(Frame.Text(json))
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                logger.info(e) { "Server -> client writer stopped" }
                return
            } catch (e: Throwable) {
                logger.warn(e) { "Server -> client send failed" }
                return
            }
        }
    }

    private fun outboundConsumer(): MessageConsumer {
        return MessageConsumer {
            if (it is ResponseMessage) {
                recordResponse(it)
            }
            val json = jsonHandler.serialize(it)
            // we want to throw if the channel is full -> probably dead connection
            val result = outgoing.trySend(json)
            if (result.isClosed) {
                // raced the session close
                return@MessageConsumer
            }
            if (result.isFailure) {
                throw IllegalStateException("WebSocket outgoing buffer overflowed (capacity=$OUTGOING_BUFFER)")
            }
        }
    }

    private fun recordResponse(response: ResponseMessage) {
        val request = pendingRequests.remove(response.id) ?: return
        Timer.builder("semantifyr.live.language.requests")
            .tag("method", request.method)
            .serviceLevelObjectives(*requestDurationBuckets)
            .register(registry)
            .record(request.receivedAt.elapsedNow().toJavaDuration())
    }

    companion object {
        private const val OUTGOING_BUFFER = 8
    }
}
