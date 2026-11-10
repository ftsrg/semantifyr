/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.lsp.session

import com.google.inject.Inject
import hu.bme.mit.semantifyr.live.backend.data.ActiveVerificationInfo
import hu.bme.mit.semantifyr.live.backend.data.SessionInfo
import hu.bme.mit.semantifyr.live.backend.data.VerificationState

@SessionScoped
class SessionInfoBuilder @Inject constructor(
    private val sessionContext: SessionContext,
    private val sessionRunContext: SessionRunContext,
    private val sessionVerificationManager: SessionVerificationManager,
) {
    fun build(): SessionInfo {
        val verifications = sessionVerificationManager.active()
        return SessionInfo(
            sessionId = sessionContext.sessionId,
            remoteIp = sessionContext.remoteIp,
            flavorId = sessionContext.flavor.id,
            kind = sessionContext.kind.id,
            uptime = sessionRunContext.startMark.elapsedNow(),
            runningCount = verifications.count {
                it.state == VerificationState.Running
            },
            queuedCount = verifications.count {
                it.state == VerificationState.Queued
            },
            workingDirectory = sessionContext.workingDirectoryPath.toString(),
            activeVerifications = verifications.map {
                ActiveVerificationInfo(
                    verificationId = it.verificationId,
                    portfolioId = it.portfolioId,
                    kind = it.kind,
                    state = it.state,
                    elapsed = it.elapsed,
                )
            },
        )
    }
}
