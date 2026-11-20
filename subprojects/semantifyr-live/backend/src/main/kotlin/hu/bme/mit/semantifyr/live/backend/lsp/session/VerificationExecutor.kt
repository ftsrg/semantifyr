/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.lsp.session

import com.google.gson.JsonElement
import com.google.inject.Singleton
import hu.bme.mit.semantifyr.lang.ide.server.commands.CommandGson
import hu.bme.mit.semantifyr.lang.ide.server.wire.VerificationCaseRequest
import hu.bme.mit.semantifyr.lang.ide.server.wire.VerificationCaseResult
import hu.bme.mit.semantifyr.lang.ide.server.wire.WitnessValidationRequest
import hu.bme.mit.semantifyr.live.backend.exceptions.SyntaxErrorException
import hu.bme.mit.semantifyr.live.backend.lsp.document.SessionDocumentManager
import hu.bme.mit.semantifyr.live.backend.lsp.service.SessionRequestManager
import hu.bme.mit.semantifyr.logging.loggerFactory
import hu.bme.mit.semantifyr.logging.warn
import kotlinx.coroutines.future.await
import org.eclipse.lsp4j.ExecuteCommandParams

interface VerificationExecutor {
    suspend fun execute(
        sessionRequestManager: SessionRequestManager,
        sessionDocumentManager: SessionDocumentManager,
        params: ExecuteCommandParams,
    ): Any?
}

@Singleton
class LiveVerificationExecutor : VerificationExecutor {

    private val logger by loggerFactory()

    override suspend fun execute(
        sessionRequestManager: SessionRequestManager,
        sessionDocumentManager: SessionDocumentManager,
        params: ExecuteCommandParams,
    ): Any? {
        ensureDocumentAvailable(sessionRequestManager, sessionDocumentManager, params)
        rejectSyntaxErrors(sessionRequestManager, sessionDocumentManager, params)
        val result = sessionRequestManager.executeCommand(withServerWitnessUri(sessionDocumentManager, params)).await()
        return withClientUris(sessionDocumentManager, result)
    }

    private fun withServerWitnessUri(sessionDocumentManager: SessionDocumentManager, params: ExecuteCommandParams): ExecuteCommandParams {
        val argument = params.arguments?.firstOrNull() as? JsonElement ?: return params
        val request = CommandGson.INSTANCE.fromJson(argument, WitnessValidationRequest::class.java) ?: return params
        val witnessUri = request.witnessUri() ?: return params
        val serverRequest = WitnessValidationRequest(request.uri(), request.range(), request.portfolio(), sessionDocumentManager.workspaceUriOf(witnessUri))
        return ExecuteCommandParams(params.command, listOf(CommandGson.INSTANCE.toJsonTree(serverRequest)))
    }

    private suspend fun ensureDocumentAvailable(
        sessionRequestManager: SessionRequestManager,
        sessionDocumentManager: SessionDocumentManager,
        params: ExecuteCommandParams,
    ) {
        val argument = params.arguments?.firstOrNull() as? JsonElement ?: return
        val uri = CommandGson.INSTANCE.fromJson(argument, VerificationCaseRequest::class.java)?.uri() ?: return
        if (sessionDocumentManager.find(uri) != null || !sessionDocumentManager.existsOnDisk(uri)) {
            return
        }
        sessionRequestManager.runWrite(
            {
                sessionDocumentManager.openExistingByClient(uri)
            },
            { _, _ ->
            },
        ).await()
    }

    private suspend fun rejectSyntaxErrors(
        sessionRequestManager: SessionRequestManager,
        sessionDocumentManager: SessionDocumentManager,
        params: ExecuteCommandParams,
    ) {
        val argument = params.arguments?.firstOrNull() as? JsonElement ?: return
        val uri = CommandGson.INSTANCE.fromJson(argument, VerificationCaseRequest::class.java)?.uri() ?: return
        val document = sessionDocumentManager.find(uri) ?: return
        val syntaxError = sessionRequestManager.runRead {
            document.resource.errors.firstOrNull()
        }.await() ?: return
        throw SyntaxErrorException("The model has a syntax error at line ${syntaxError.line}, column ${syntaxError.column}: ${syntaxError.message}")
    }

    private fun withClientUris(sessionDocumentManager: SessionDocumentManager, result: Any?): Any? {
        return when (result) {
            is VerificationCaseResult -> VerificationCaseResult(
                result.status(),
                result.message(),
                result.portfolioId(),
                result.report(),
                clientUriOf(sessionDocumentManager, result.witnessUri()),
                clientUriOf(sessionDocumentManager, result.traceUri()),
            )
            else -> result
        }
    }

    private fun clientUriOf(sessionDocumentManager: SessionDocumentManager, serverUri: String?): String? {
        serverUri ?: return null
        val clientUri = sessionDocumentManager.toClientUri(serverUri)
        if (clientUri == serverUri) {
            logger.warn { "Artifact URI is not inside the workspace, leaving it untouched: $serverUri" }
        }
        return clientUri
    }
}
