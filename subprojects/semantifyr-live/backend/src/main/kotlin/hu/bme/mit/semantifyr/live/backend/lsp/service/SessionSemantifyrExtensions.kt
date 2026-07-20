/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.lsp.service

import com.google.inject.Inject
import hu.bme.mit.semantifyr.live.backend.data.SessionInfo
import hu.bme.mit.semantifyr.live.backend.lsp.document.SessionDocumentManager
import hu.bme.mit.semantifyr.live.backend.lsp.session.SessionInfoBuilder
import hu.bme.mit.semantifyr.live.backend.lsp.session.SessionScoped
import hu.bme.mit.semantifyr.live.backend.lsp.session.SessionVerificationManager
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest
import java.util.concurrent.CompletableFuture

@SessionScoped
class SessionSemantifyrExtensions @Inject constructor(
    private val sessionInfoBuilder: SessionInfoBuilder,
    private val sessionDocumentManager: SessionDocumentManager,
    private val sessionVerificationManager: SessionVerificationManager,
) {

    @JsonRequest(value = SemantifyrLiveMethods.SESSION_INFO, useSegment = false)
    fun sessionInfo(): CompletableFuture<SessionInfo> {
        return CompletableFuture.completedFuture(sessionInfoBuilder.build())
    }

    @JsonRequest(value = SemantifyrLiveMethods.READ_DOCUMENT, useSegment = false)
    fun readDocument(params: ReadDocumentParams): CompletableFuture<ReadDocumentResult> {
        return CompletableFuture.completedFuture(
            ReadDocumentResult(sessionDocumentManager.readDocumentText(params.uri)),
        )
    }

    @JsonRequest(value = SemantifyrLiveMethods.LIST_VERIFICATIONS, useSegment = false)
    fun listVerifications(): CompletableFuture<VerificationsChangedParams> {
        return CompletableFuture.completedFuture(VerificationsChangedParams(sessionVerificationManager.active()))
    }

    @JsonRequest(value = SemantifyrLiveMethods.CANCEL_VERIFICATION, useSegment = false)
    fun cancelVerification(params: CancelVerificationParams): CompletableFuture<Boolean> {
        return CompletableFuture.completedFuture(sessionVerificationManager.cancel(params.verificationId))
    }

    @JsonRequest(value = SemantifyrLiveMethods.CANCEL_ALL_VERIFICATIONS, useSegment = false)
    fun cancelAllVerifications(): CompletableFuture<Int> {
        return CompletableFuture.completedFuture(sessionVerificationManager.cancelAll())
    }
}
