/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.lsp.service

import com.google.gson.JsonElement
import hu.bme.mit.semantifyr.lang.ide.server.commands.CommandGson
import hu.bme.mit.semantifyr.live.backend.data.VerificationKind
import hu.bme.mit.semantifyr.live.backend.lsp.session.LspSession
import hu.bme.mit.semantifyr.live.backend.lsp.session.VerificationExecutor
import hu.bme.mit.semantifyr.live.backend.lsp.session.VerificationManager
import hu.bme.mit.semantifyr.live.backend.lsp.session.VerificationRequest
import org.eclipse.lsp4j.DidChangeConfigurationParams
import org.eclipse.lsp4j.DidChangeWatchedFilesParams
import org.eclipse.lsp4j.ExecuteCommandParams
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode
import org.eclipse.lsp4j.services.WorkspaceService
import java.util.concurrent.CompletableFuture

/**
 * The argument the live backend's verify / validateWitness command carries. The language layer
 * reads its own `VerificationCaseRequest` ({@code uri} / {@code range} / {@code portfolio}) from
 * the same JSON; the extra {@code caseLabel} / {@code requestId} are live-backend-only fields used
 * for in-flight tracking. Parsed out of {@code ExecuteCommandParams.arguments[0]}.
 */
data class VerificationCommandArguments(
    val uri: String? = null,
    val requestId: String? = null,
    val caseLabel: String? = null,
    val portfolio: String? = null,
) {
    companion object {
        fun parse(params: ExecuteCommandParams): VerificationCommandArguments? {
            val argument = params.arguments?.firstOrNull() as? JsonElement ?: return null
            return CommandGson.INSTANCE.fromJson(argument, VerificationCommandArguments::class.java)
        }
    }
}

class SessionWorkspaceService(
    private val lspSession: LspSession,
    private val verificationManager: VerificationManager,
    private val verificationExecutor: VerificationExecutor,
) : WorkspaceService {

    private val throttledCommands = setOfNotNull(
        lspSession.flavor.verificationCommand,
        lspSession.flavor.validateWitnessCommand,
    )

    override fun didChangeConfiguration(params: DidChangeConfigurationParams) {
        // ignored: server settings come from BackendConfig at startup
    }

    override fun didChangeWatchedFiles(params: DidChangeWatchedFilesParams) {
        // ignored: per-session in-memory model has no watched files
    }

    override fun executeCommand(params: ExecuteCommandParams): CompletableFuture<Any?> {
        lspSession.sessionDocumentManager.flushAllToDisk()
        return if (params.command in throttledCommands) {
            verificationManager.run(lspSession, parseRequest(params)) {
                verificationExecutor.execute(lspSession, params)
            }
        } else {
            lspSession.executeCommandUnderReadLock(params)
        }
    }

    private fun parseRequest(params: ExecuteCommandParams): VerificationRequest {
        val arguments = VerificationCommandArguments.parse(params) ?: throw ResponseErrorException(
            ResponseError(ResponseErrorCode.InvalidParams, "Missing verification command argument for ${params.command}", null),
        )
        val caseLabel = arguments.caseLabel ?: throw ResponseErrorException(
            ResponseError(ResponseErrorCode.InvalidParams, "Missing 'caseLabel' verification argument", null),
        )
        val portfolio = arguments.portfolio ?: throw ResponseErrorException(
            ResponseError(ResponseErrorCode.InvalidParams, "Missing 'portfolio' verification argument", null),
        )
        val kind = if (params.command == lspSession.flavor.validateWitnessCommand) {
            VerificationKind.Validate
        } else {
            VerificationKind.Verify
        }
        return VerificationRequest(
            requestId = arguments.requestId,
            kind = kind,
            caseLabel = caseLabel,
            portfolioId = portfolio,
        )
    }
}
