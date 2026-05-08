/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.lsp.bridge

import hu.bme.mit.semantifyr.live.backend.lsp.LspMessageHandlerBuilder
import hu.bme.mit.semantifyr.live.backend.server.ActiveVerificationInfo
import hu.bme.mit.semantifyr.live.backend.server.SessionInfo

object SemantifyrLiveMethods {
    const val SESSION_INFO = "semantifyr/live/session/info"
    const val INFLIGHT_LIST = "semantifyr/live/session/inflight/list"
    const val INFLIGHT_CANCEL = "semantifyr/live/session/inflight/cancel"
    const val INFLIGHT_CANCEL_ALL = "semantifyr/live/session/inflight/cancelAll"
    const val INFLIGHT_CHANGED = "semantifyr/live/session/inflight/changed"
    const val VERIFICATION_ENQUEUED = "semantifyr/live/session/verification/enqueued"
}

data class InflightCancelParams(
    val requestId: String,
)

data class InflightChangedParams(
    val inflight: List<ActiveVerificationInfo>,
)

data class VerificationEnqueuedParams(
    val requestId: String,
)

fun LspMessageHandlerBuilder.registerSemantifyrLiveMethods(): LspMessageHandlerBuilder {
    return addRequest(SemantifyrLiveMethods.SESSION_INFO, SessionInfo::class.java)
        .addRequest(SemantifyrLiveMethods.INFLIGHT_LIST, InflightChangedParams::class.java)
        .addRequest(SemantifyrLiveMethods.INFLIGHT_CANCEL, Boolean::class.java, InflightCancelParams::class.java)
        .addRequest(SemantifyrLiveMethods.INFLIGHT_CANCEL_ALL, Int::class.java)
        .addNotification(SemantifyrLiveMethods.INFLIGHT_CHANGED, InflightChangedParams::class.java)
        .addNotification(SemantifyrLiveMethods.VERIFICATION_ENQUEUED, VerificationEnqueuedParams::class.java)
}
