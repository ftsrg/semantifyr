/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.lsp.bridge

import com.google.inject.Inject
import hu.bme.mit.semantifyr.live.backend.FlavorRegistry
import hu.bme.mit.semantifyr.live.backend.session.SessionScoped
import hu.bme.mit.semantifyr.logging.info
import hu.bme.mit.semantifyr.logging.loggerFactory
import org.eclipse.lsp4j.ExecuteCommandParams
import org.eclipse.lsp4j.jsonrpc.messages.Message
import org.eclipse.lsp4j.jsonrpc.messages.RequestMessage
import org.eclipse.lsp4j.jsonrpc.messages.ResponseMessage

interface SessionVerificationManager {
    suspend fun enqueueVerification(requestId: String, requestMessage: String)
    suspend fun completeVerification(requestId: String, responseMessage: String)
    suspend fun cancelVerification(requestId: String)

    fun isVerificationTracked(requestId: String): Boolean
}

@SessionScoped
class VerificationMessageInterceptor @Inject constructor(
    private val sessionVerificationManager: SessionVerificationManager,
) : LspMessageInterceptor {

    private val logger by loggerFactory()

    override suspend fun handleClientMessage(
        raw: String,
        message: Message,
        bridge: LspBridge,
    ): Boolean {
        if (message !is RequestMessage) {
            return true
        }

        val params = message.params as? ExecuteCommandParams ?: return true
        if (params.command !in verificationCommands) {
            return true
        }

        val requestId = message.id ?: return true // we should handle malformed requests by sending an error instead
        logger.info { "Intercepted verification request (command=${params.command}, requestId=$requestId)" }
        sessionVerificationManager.enqueueVerification(requestId, raw)
        return false
    }

    override suspend fun handleServerMessage(
        raw: String,
        message: Message,
        bridge: LspBridge,
    ): Boolean {
        if (message !is ResponseMessage) {
            return true
        }

        val requestId = message.id ?: return true // we should handle malformed responses
        if (!sessionVerificationManager.isVerificationTracked(requestId)) {
            return true
        }

        logger.info { "Intercepted verification response (requestId=$requestId)" }
        sessionVerificationManager.completeVerification(requestId, raw)
        return false
    }

    companion object {
        private val verificationCommands = FlavorRegistry.flavors.mapNotNull {
            it.verificationCommand
        }.toSet()
    }
}
