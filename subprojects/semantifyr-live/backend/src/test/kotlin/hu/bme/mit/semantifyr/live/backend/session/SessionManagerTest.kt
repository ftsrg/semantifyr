/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.session

import hu.bme.mit.semantifyr.live.backend.lsp.session.SessionManager
import hu.bme.mit.semantifyr.live.backend.testing.handler
import hu.bme.mit.semantifyr.live.backend.testing.testInjector
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SessionManagerTest {

    @Test
    fun `activeSessions starts at zero`() {
        val manager = testInjector().handler<SessionManager>()
        assertThat(manager.activeSessions).isEqualTo(0)
        assertThat(manager.maxSessions).isEqualTo(256)
    }

    @Test
    fun `getSessionInfos returns empty list when no live sessions`() {
        val manager = testInjector().handler<SessionManager>()
        assertThat(manager.getSessionInfos()).isEmpty()
    }

    @Test
    fun `cancelSession returns false for an unknown sessionId`() {
        val manager = testInjector().handler<SessionManager>()
        assertThat(manager.cancelSession("nonexistent")).isFalse()
    }

    @Test
    fun `cancelVerification returns false for an unknown sessionId`() {
        val manager = testInjector().handler<SessionManager>()
        assertThat(manager.cancelVerification("nonexistent", "request-1")).isFalse()
    }

    @Test
    fun `close on an empty manager does not throw`() {
        val manager = testInjector().handler<SessionManager>()
        manager.close()
        assertThat(manager.activeSessions).isEqualTo(0)
    }
}
