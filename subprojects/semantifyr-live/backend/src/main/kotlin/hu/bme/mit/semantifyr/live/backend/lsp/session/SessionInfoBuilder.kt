/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.lsp.session

import com.google.inject.Inject
import hu.bme.mit.semantifyr.live.backend.data.ActiveVerificationInfo
import hu.bme.mit.semantifyr.live.backend.data.SessionInfo

@SessionScoped
class SessionInfoBuilder @Inject constructor(
    private val sessionContext: SessionContext,
    private val sessionRunContext: SessionRunContext,
    private val sessionVerificationManager: SessionVerificationManager,
) {
    fun build(): SessionInfo {
        return SessionInfo(
            sessionId = sessionContext.sessionId,
            remoteIp = sessionContext.remoteIp,
            flavorId = sessionContext.flavor.id,
            uptime = sessionRunContext.startMark.elapsedNow(),
            workingDirectory = sessionContext.workingDirectoryPath.toString(),
            activeVerifications = sessionVerificationManager.active().map {
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
