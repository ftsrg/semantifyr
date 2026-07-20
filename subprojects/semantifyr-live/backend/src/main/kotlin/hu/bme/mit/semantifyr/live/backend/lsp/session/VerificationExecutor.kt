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
import hu.bme.mit.semantifyr.lang.ide.server.wire.VerificationTrace
import hu.bme.mit.semantifyr.live.backend.lsp.document.SessionDocumentManager
import hu.bme.mit.semantifyr.live.backend.lsp.service.SessionRequestManager
import hu.bme.mit.semantifyr.live.backend.lsp.service.runWrite
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
        val result = sessionRequestManager.executeCommand(params).await()
        return rewriteWitnessUri(sessionDocumentManager, result)
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
        sessionRequestManager.runWrite {
            sessionDocumentManager.openExistingByClient(uri)
        }.await()
    }

    private fun rewriteWitnessUri(sessionDocumentManager: SessionDocumentManager, result: Any?): Any? {
        if (result !is VerificationCaseResult) {
            return result
        }
        val trace = result.trace() ?: return result
        val serverUri = trace.witnessUri() ?: return result
        val clientUri = sessionDocumentManager.toClientUri(serverUri)
        if (clientUri == serverUri) {
            logger.warn { "Witness URI is not inside the workspace, leaving it untouched: $serverUri" }
            return result
        }
        return VerificationCaseResult(
            result.status(),
            result.message(),
            result.backendId(),
            result.portfolioId(),
            result.metrics(),
            VerificationTrace(trace.callTrace(), trace.witnessState(), clientUri),
        )
    }
}
