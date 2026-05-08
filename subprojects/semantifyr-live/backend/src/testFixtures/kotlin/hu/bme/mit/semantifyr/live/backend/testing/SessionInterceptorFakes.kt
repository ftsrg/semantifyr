/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.testing

import hu.bme.mit.semantifyr.live.backend.lsp.bridge.SessionControlManager
import hu.bme.mit.semantifyr.live.backend.lsp.bridge.SessionInfoProvider
import hu.bme.mit.semantifyr.live.backend.lsp.bridge.SessionVerificationManager
import hu.bme.mit.semantifyr.live.backend.server.ActiveVerificationInfo
import hu.bme.mit.semantifyr.live.backend.server.SessionInfo
import hu.bme.mit.semantifyr.live.backend.server.VerificationKind

class FakeSessionInfoProvider(private val info: SessionInfo) : SessionInfoProvider {
    override fun getSessionInfo(): SessionInfo {
        return info
    }
}

class FakeSessionControlManager : SessionControlManager {
    var inflight: List<ActiveVerificationInfo> = emptyList()
    val cancelled = mutableListOf<String>()
    var cancelAllCount: Int = 0

    override fun listInFlight(): List<ActiveVerificationInfo> {
        return inflight
    }

    override suspend fun cancelInFlight(requestId: String): Boolean {
        cancelled += requestId
        return inflight.any { it.requestId == requestId }
    }

    override suspend fun cancelAllInFlight(): Int {
        cancelAllCount = inflight.size
        return cancelAllCount
    }
}

class FakeSessionVerificationManager : SessionVerificationManager {
    data class Enqueued(
        val requestId: String,
        val requestMessage: String,
        val kind: VerificationKind,
        val caseLabel: String?,
        val portfolioId: String?,
    )

    data class Completed(
        val requestId: String,
        val responseMessage: String,
    )

    val enqueued = mutableListOf<Enqueued>()
    val completed = mutableListOf<Completed>()
    val cancelled = mutableListOf<String>()
    var tracked: Set<String> = emptySet()

    override suspend fun enqueueVerification(
        requestId: String,
        requestMessage: String,
        kind: VerificationKind,
        caseLabel: String?,
        portfolioId: String?,
    ) {
        enqueued += Enqueued(requestId, requestMessage, kind, caseLabel, portfolioId)
    }

    override fun isVerificationTracked(requestId: String): Boolean {
        return requestId in tracked
    }

    override suspend fun completeVerification(requestId: String, responseMessage: String) {
        completed += Completed(requestId, responseMessage)
    }

    override suspend fun cancelVerification(requestId: String) {
        cancelled += requestId
    }
}
