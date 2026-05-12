/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.lsp.service

import hu.bme.mit.semantifyr.live.backend.data.ActiveVerificationInfo

object SemantifyrLiveMethods {
    const val SESSION_INFO = "semantifyr/live/session/info"
    const val READ_DOCUMENT = "semantifyr/live/session/document/read"
    const val LIST_VERIFICATIONS = "semantifyr/live/session/verification/list"
    const val CANCEL_VERIFICATION = "semantifyr/live/session/verification/cancel"
    const val CANCEL_ALL_VERIFICATIONS = "semantifyr/live/session/verification/cancel/all"
    const val VERIFICATIONS_CHANGED = "semantifyr/live/session/verification/changed"
}

data class CancelVerificationParams(
    val requestId: String,
)

data class VerificationsChangedParams(
    val active: List<ActiveVerificationInfo>,
)

data class ReadDocumentParams(
    val uri: String,
)

data class ReadDocumentResult(
    val text: String?,
)
