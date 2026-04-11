/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.session

import hu.bme.mit.semantifyr.live.backend.utils.error
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.eclipse.lsp4j.jsonrpc.messages.Message
import org.eclipse.lsp4j.jsonrpc.messages.RequestMessage
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError
import org.eclipse.lsp4j.jsonrpc.messages.ResponseMessage
import hu.bme.mit.semantifyr.live.backend.utils.info
import hu.bme.mit.semantifyr.live.backend.utils.loggerFactory
import hu.bme.mit.semantifyr.live.backend.utils.warn

class SessionVerificationManager(
    private val gate: VerificationGate,
    private val scope: CoroutineScope,
    private val sendCancelToLsp: suspend (String) -> Unit,
    private val sendErrorToClient: suspend (ResponseMessage) -> Unit,
) {

    private val logger by loggerFactory()

    private val inFlight = mutableMapOf<String, Job>()

    /**
     * @return true if the message was consumed and should not be forwarded.
     */
    suspend fun handleClientMessage(message: Message): Boolean {
        if (message !is RequestMessage) return false
        if (!gate.isVerificationRequest(message)) return false

        val requestId = message.id ?: return false

        try {
            val job = gate.registerVerification(scope) {
                onTimeout(requestId)
            }

            job.invokeOnCompletion {
                inFlight.remove(requestId)
            }

            inFlight[requestId] = job

            logger.info { "Verification started (requestId=$requestId) available slots: ${gate.availablePermits}" }
        } catch (e: VerificationLimitReachedException) {
            logger.warn { "Verification rejected (requestId=$requestId) (queue full)" }
            sendErrorToClient(errorResponse(requestId, -32000, e.message ?: "Verification queue full"))
            return true
        }

        return false
    }

    /**
     * @return true if the message was consumed and should not be forwarded.
     */
    @Suppress("SameReturnValue")
    fun handleServerMessage(message: Message): Boolean {
        if (message !is ResponseMessage) return false

        val requestId = message.id ?: return false

        val job = inFlight.remove(requestId) ?: return false
        job.cancel()

        logger.info { "Verification completed (requestId=$requestId)" }

        return false
    }

    fun releaseAll() {
        for ((requestId, job) in inFlight) {
            job.cancel()
            logger.info { "Releasing orphaned verification permit (requestId=$requestId)" }
        }
        inFlight.clear()
    }

    private suspend fun onTimeout(requestId: String) {
        logger.info { "Verification timed out (requestId=$requestId)" }
        sendCancelToLsp(requestId)
        sendErrorToClient(errorResponse(requestId, -32800, "Verification timed out"))
    }

    private fun errorResponse(id: String, code: Int, message: String): ResponseMessage {
        return ResponseMessage().apply {
            this.id = id
            this.error = ResponseError(code, message, null)
        }
    }
}
