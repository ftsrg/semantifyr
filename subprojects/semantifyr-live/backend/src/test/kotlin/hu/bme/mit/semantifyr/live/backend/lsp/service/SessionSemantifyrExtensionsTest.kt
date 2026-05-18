/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.lsp.service

import hu.bme.mit.semantifyr.live.backend.data.SessionInfo
import hu.bme.mit.semantifyr.live.backend.data.VerificationKind
import hu.bme.mit.semantifyr.live.backend.data.VerificationState
import hu.bme.mit.semantifyr.live.backend.lsp.document.SessionDocumentManager
import hu.bme.mit.semantifyr.live.backend.lsp.session.SessionInfoBuilder
import hu.bme.mit.semantifyr.live.backend.lsp.session.SessionVerificationManager
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import kotlin.time.Duration

class SessionSemantifyrExtensionsTest {

    private fun sessionInfo() = SessionInfo(
        sessionId = "session-1",
        remoteIp = "127.0.0.1",
        flavorId = "oxsts",
        uptime = Duration.ZERO,
        workingDirectory = "/tmp/session-1",
        activeVerifications = emptyList(),
    )

    @Test
    fun `sessionInfo returns the session's current info`() {
        val info = sessionInfo()
        val sessionInfoBuilder = mock<SessionInfoBuilder> {
            on { build() } doReturn info
        }
        val extensions = SessionSemantifyrExtensions(
            sessionInfoBuilder,
            mock<SessionDocumentManager>(),
            mock<SessionVerificationManager>(),
        )

        assertThat(extensions.sessionInfo().get()).isSameAs(info)
    }

    @Test
    fun `listVerifications returns this session's active verifications`() {
        val active = listOf(
            RunningVerification(
                verificationId = "r-1",
                location = Location("file:///x.oxsts", Range(Position(0, 0), Position(0, 0))),
                portfolioId = "smart-full",
                kind = VerificationKind.Verify,
                state = VerificationState.Running,
                elapsed = Duration.ZERO,
            ),
        )
        val sessionVerificationManager = mock<SessionVerificationManager> {
            on { active() } doReturn active
        }
        val extensions = SessionSemantifyrExtensions(
            mock<SessionInfoBuilder>(),
            mock<SessionDocumentManager>(),
            sessionVerificationManager,
        )

        assertThat(extensions.listVerifications().get().active).isEqualTo(active)
    }

    @Test
    fun `cancelVerification delegates to the session manager and returns the result`() {
        val sessionVerificationManager = mock<SessionVerificationManager> {
            on { cancel("r-1") } doReturn true
        }
        val extensions = SessionSemantifyrExtensions(
            mock<SessionInfoBuilder>(),
            mock<SessionDocumentManager>(),
            sessionVerificationManager,
        )

        assertThat(extensions.cancelVerification(CancelVerificationParams(verificationId = "r-1")).get()).isTrue()
        verify(sessionVerificationManager).cancel("r-1")
    }

    @Test
    fun `cancelAllVerifications cancels everything for this session`() {
        val sessionVerificationManager = mock<SessionVerificationManager> {
            on { cancelAll() } doReturn 3
        }
        val extensions = SessionSemantifyrExtensions(
            mock<SessionInfoBuilder>(),
            mock<SessionDocumentManager>(),
            sessionVerificationManager,
        )

        assertThat(extensions.cancelAllVerifications().get()).isEqualTo(3)
        verify(sessionVerificationManager).cancelAll()
    }
}
