/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.lsp.service

import com.google.gson.JsonElement
import hu.bme.mit.semantifyr.lang.ide.server.commands.CommandGson
import hu.bme.mit.semantifyr.lang.ide.server.wire.VerificationCaseRequest
import hu.bme.mit.semantifyr.lang.ide.server.wire.VerificationCaseSpecification
import hu.bme.mit.semantifyr.live.backend.data.VerificationKind
import hu.bme.mit.semantifyr.live.backend.lsp.session.LspSession
import hu.bme.mit.semantifyr.live.backend.lsp.session.VerificationExecutor
import hu.bme.mit.semantifyr.live.backend.lsp.session.VerificationManager
import org.eclipse.lsp4j.DidChangeConfigurationParams
import org.eclipse.lsp4j.DidChangeWatchedFilesParams
import org.eclipse.lsp4j.ExecuteCommandParams
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode
import org.eclipse.lsp4j.services.WorkspaceService
import java.util.concurrent.CompletableFuture

class SessionWorkspaceService(
    private val lspSession: LspSession,
    private val verificationManager: VerificationManager,
    private val verificationExecutor: VerificationExecutor,
) : WorkspaceService {

    override fun didChangeConfiguration(params: DidChangeConfigurationParams) {
        // no op
    }

    override fun didChangeWatchedFiles(params: DidChangeWatchedFilesParams) {
        // no op
    }

    override fun executeCommand(params: ExecuteCommandParams): CompletableFuture<Any?> {
        lspSession.sessionDocumentManager.flushAllToDisk()
        val kind = verificationKindOf(params.command)
        if (kind == null) {
            return lspSession.executeCommandUnderReadLock(params).thenApply {
                rewriteResponseUris(params, it)
            }
        }
        val request = parseVerificationCaseRequest(params)
        return verificationManager.run(lspSession, request, kind) {
            verificationExecutor.execute(lspSession, params)
        }
    }

    private fun rewriteResponseUris(params: ExecuteCommandParams, result: Any?): Any? {
        if (params.command != lspSession.flavor.discoveryCommand) {
            return result
        }
        val list = result as? List<*> ?: return result
        return list.map {
            if (it is VerificationCaseSpecification) {
                val serverUri = it.location().uri
                val clientUri = lspSession.sessionDocumentManager.toClientUri(serverUri)
                if (clientUri == serverUri) {
                    it
                } else {
                    VerificationCaseSpecification(it.id(), it.label(), Location(clientUri, it.location().range))
                }
            } else {
                it
            }
        }
    }

    private fun verificationKindOf(command: String): VerificationKind? {
        return when (command) {
            lspSession.flavor.verificationCommand -> VerificationKind.Verify
            lspSession.flavor.validateWitnessCommand -> VerificationKind.Validate
            else -> null
        }
    }

    private fun parseVerificationCaseRequest(params: ExecuteCommandParams): VerificationCaseRequest {
        val argument = params.arguments?.firstOrNull() as? JsonElement
            ?: throw ResponseErrorException(
                ResponseError(ResponseErrorCode.InvalidParams, "${params.command} expects a VerificationCaseRequest argument", null),
            )
        val request = CommandGson.INSTANCE.fromJson(argument, VerificationCaseRequest::class.java)
            ?: throw ResponseErrorException(
                ResponseError(ResponseErrorCode.InvalidParams, "${params.command} argument is not a VerificationCaseRequest", null),
            )
        if (request.uri().isNullOrBlank() || request.range() == null || request.portfolio().isNullOrBlank()) {
            throw ResponseErrorException(
                ResponseError(ResponseErrorCode.InvalidParams, "${params.command} requires a uri, a range and a portfolio", null),
            )
        }
        return request
    }
}
