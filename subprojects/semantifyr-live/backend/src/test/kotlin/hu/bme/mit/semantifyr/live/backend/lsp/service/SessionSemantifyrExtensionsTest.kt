/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.lsp.service

import hu.bme.mit.semantifyr.live.backend.data.ActiveVerificationInfo
import hu.bme.mit.semantifyr.live.backend.data.SessionInfo
import hu.bme.mit.semantifyr.live.backend.data.SessionLspInfo
import hu.bme.mit.semantifyr.live.backend.lsp.session.LspSession
import hu.bme.mit.semantifyr.live.backend.lsp.session.VerificationManager
import org.assertj.core.api.Assertions.assertThat
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
        sessionLspInfo = SessionLspInfo(Duration.ZERO, Duration.ZERO),
    )

    @Test
    fun `sessionInfo returns the session's current info`() {
        val info = sessionInfo()
        val lspSession = mock<LspSession> {
            on { currentSessionInfo() } doReturn info
        }
        val extensions = SessionSemantifyrExtensions(lspSession, mock())

        assertThat(extensions.sessionInfo().get()).isSameAs(info)
    }

    @Test
    fun `listVerifications returns this session's active verifications`() {
        val active = listOf(ActiveVerificationInfo(requestId = "r-1"))
        val verificationManager = mock<VerificationManager> {
            on { activeFor("session-1") } doReturn active
        }
        val lspSession = mock<LspSession> {
            on { sessionId } doReturn "session-1"
        }
        val extensions = SessionSemantifyrExtensions(lspSession, verificationManager)

        assertThat(extensions.listVerifications().get().active).isEqualTo(active)
    }

    @Test
    fun `cancelVerification delegates to the manager and returns the result`() {
        val verificationManager = mock<VerificationManager> {
            on { cancel("r-1") } doReturn true
        }
        val extensions = SessionSemantifyrExtensions(mock(), verificationManager)

        assertThat(extensions.cancelVerification(CancelVerificationParams("r-1")).get()).isTrue()
        verify(verificationManager).cancel("r-1")
    }

    @Test
    fun `cancelAllVerifications cancels everything for this session`() {
        val verificationManager = mock<VerificationManager> {
            on { cancelForSession("session-1") } doReturn 3
        }
        val lspSession = mock<LspSession> {
            on { sessionId } doReturn "session-1"
        }
        val extensions = SessionSemantifyrExtensions(lspSession, verificationManager)

        assertThat(extensions.cancelAllVerifications().get()).isEqualTo(3)
        verify(verificationManager).cancelForSession("session-1")
    }
}
