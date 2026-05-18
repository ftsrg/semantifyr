/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.lsp.service

import hu.bme.mit.semantifyr.live.backend.data.VerificationKind
import hu.bme.mit.semantifyr.live.backend.data.VerificationState
import org.eclipse.lsp4j.Location
import kotlin.time.Duration

object SemantifyrLiveMethods {
    const val SESSION_INFO = "semantifyr/live/session/info"
    const val READ_DOCUMENT = "semantifyr/live/session/document/read"
    const val LIST_VERIFICATIONS = "semantifyr/live/session/verification/list"
    const val CANCEL_VERIFICATION = "semantifyr/live/session/verification/cancel"
    const val CANCEL_ALL_VERIFICATIONS = "semantifyr/live/session/verification/cancel/all"
    const val VERIFICATIONS_CHANGED = "semantifyr/live/session/verification/changed"
}

data class CancelVerificationParams(
    val verificationId: String,
)

data class RunningVerification(
    val verificationId: String,
    val location: Location,
    val portfolioId: String,
    val kind: VerificationKind,
    val state: VerificationState,
    val elapsed: Duration,
)

data class VerificationsChangedParams(
    val active: List<RunningVerification>,
)

data class ReadDocumentParams(
    val uri: String,
)

data class ReadDocumentResult(
    val text: String?,
)
