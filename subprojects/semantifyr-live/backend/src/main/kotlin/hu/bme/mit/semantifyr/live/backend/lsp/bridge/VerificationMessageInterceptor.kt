/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.lsp.bridge

import com.google.gson.JsonObject
import com.google.inject.Inject
import hu.bme.mit.semantifyr.live.backend.server.FlavorRegistry
import hu.bme.mit.semantifyr.live.backend.server.VerificationKind
import hu.bme.mit.semantifyr.live.backend.session.SessionScoped
import hu.bme.mit.semantifyr.logging.info
import hu.bme.mit.semantifyr.logging.loggerFactory
import org.eclipse.lsp4j.ExecuteCommandParams
import org.eclipse.lsp4j.jsonrpc.messages.Message
import org.eclipse.lsp4j.jsonrpc.messages.RequestMessage
import org.eclipse.lsp4j.jsonrpc.messages.ResponseMessage

interface SessionVerificationManager {
    suspend fun enqueueVerification(
        requestId: String,
        requestMessage: String,
        kind: VerificationKind,
        caseLabel: String?,
        portfolioId: String?,
    )
    suspend fun completeVerification(requestId: String, responseMessage: String)
    suspend fun cancelVerification(requestId: String)

    fun isVerificationTracked(requestId: String): Boolean
}

@SessionScoped
class VerificationMessageInterceptor @Inject constructor(
    private val sessionVerificationManager: SessionVerificationManager,
) : LspMessageInterceptor {

    private val logger by loggerFactory()

    override suspend fun interceptClientMessage(
        raw: String,
        message: Message,
        bridge: LspBridge,
    ): Boolean {
        if (message !is RequestMessage) {
            return false
        }

        val params = message.params as? ExecuteCommandParams ?: return false
        if (params.command !in throttledCommands) {
            return false
        }

        val requestId = message.id ?: return false // we should handle malformed requests by sending an error instead
        val kind = if (params.command in validateCommands) VerificationKind.Validate else VerificationKind.Verify
        val portfolioId = extractStringArg(params, "portfolio")
        val caseLabel = extractStringArg(params, "caseLabel")
        logger.info {
            "Intercepted throttled command (command=${params.command}, kind=$kind, requestId=$requestId, case=${caseLabel ?: "?"}, portfolio=${portfolioId ?: "default"})"
        }
        sessionVerificationManager.enqueueVerification(requestId, raw, kind, caseLabel, portfolioId)
        return true
    }

    private fun extractStringArg(params: ExecuteCommandParams, name: String): String? {
        val first = params.arguments?.firstOrNull() as? JsonObject ?: return null
        val value = first.get(name) ?: return null
        if (value.isJsonPrimitive && value.asJsonPrimitive.isString) {
            val str = value.asString
            return str.ifBlank { null }
        }
        return null
    }

    override suspend fun interceptServerMessage(
        raw: String,
        message: Message,
        bridge: LspBridge,
    ): Boolean {
        if (message !is ResponseMessage) {
            return false
        }

        val requestId = message.id ?: return false // we should handle malformed responses
        if (!sessionVerificationManager.isVerificationTracked(requestId)) {
            return false
        }

        logger.info { "Intercepted verification response (requestId=$requestId)" }
        sessionVerificationManager.completeVerification(requestId, raw)
        return true
    }

    companion object {
        private val verifyCommands = FlavorRegistry.flavors.map { it.verificationCommand }.toSet()
        private val validateCommands = FlavorRegistry.flavors.map { it.validateWitnessCommand }.toSet()
        private val throttledCommands = verifyCommands + validateCommands
    }
}
