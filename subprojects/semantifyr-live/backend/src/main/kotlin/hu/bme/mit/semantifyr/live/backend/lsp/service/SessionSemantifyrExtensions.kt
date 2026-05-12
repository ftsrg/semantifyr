/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.lsp.service

import hu.bme.mit.semantifyr.live.backend.data.SessionInfo
import hu.bme.mit.semantifyr.live.backend.lsp.session.LspSession
import hu.bme.mit.semantifyr.live.backend.lsp.session.VerificationManager
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest
import java.util.concurrent.CompletableFuture

class SessionSemantifyrExtensions(
    private val lspSession: LspSession,
    private val verificationManager: VerificationManager,
) {

    @JsonRequest(value = SemantifyrLiveMethods.SESSION_INFO, useSegment = false)
    fun sessionInfo(): CompletableFuture<SessionInfo> {
        return CompletableFuture.completedFuture(lspSession.currentSessionInfo())
    }

    @JsonRequest(value = SemantifyrLiveMethods.READ_DOCUMENT, useSegment = false)
    fun readDocument(params: ReadDocumentParams): CompletableFuture<ReadDocumentResult> {
        return CompletableFuture.completedFuture(
            ReadDocumentResult(lspSession.sessionDocumentManager.readDocumentText(params.uri)),
        )
    }

    @JsonRequest(value = SemantifyrLiveMethods.LIST_VERIFICATIONS, useSegment = false)
    fun listVerifications(): CompletableFuture<VerificationsChangedParams> {
        return CompletableFuture.completedFuture(VerificationsChangedParams(verificationManager.activeFor(lspSession.sessionId)))
    }

    @JsonRequest(value = SemantifyrLiveMethods.CANCEL_VERIFICATION, useSegment = false)
    fun cancelVerification(params: CancelVerificationParams): CompletableFuture<Boolean> {
        return CompletableFuture.completedFuture(verificationManager.cancel(params.requestId))
    }

    @JsonRequest(value = SemantifyrLiveMethods.CANCEL_ALL_VERIFICATIONS, useSegment = false)
    fun cancelAllVerifications(): CompletableFuture<Int> {
        return CompletableFuture.completedFuture(verificationManager.cancelForSession(lspSession.sessionId))
    }
}
