/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.lsp.bridge

import hu.bme.mit.semantifyr.live.backend.server.ActiveVerificationInfo
import hu.bme.mit.semantifyr.live.backend.server.LspProxyInfo
import hu.bme.mit.semantifyr.live.backend.server.SessionInfo
import hu.bme.mit.semantifyr.live.backend.server.VerificationKind
import hu.bme.mit.semantifyr.live.backend.testing.FakeSessionControlManager
import hu.bme.mit.semantifyr.live.backend.testing.FakeSessionInfoProvider
import hu.bme.mit.semantifyr.live.backend.testing.LspMessages
import hu.bme.mit.semantifyr.live.backend.testing.RecordingLspBridge
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.lsp4j.jsonrpc.messages.ResponseMessage
import org.junit.jupiter.api.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class SemantifyrLiveMethodInterceptorTest {

    private val testSessionInfo = SessionInfo(
        sessionId = "s1",
        remoteIp = "127.0.0.1",
        flavorId = "oxsts",
        uptime = 10.seconds,
        workingDirectory = "/tmp/s1",
        activeVerifications = emptyList(),
        started = true,
        bridgeInfo = LspProxyInfo(0, 0, 0, Duration.ZERO, Duration.ZERO),
    )

    private fun newInterceptor(
        info: SessionInfo = testSessionInfo,
        control: SessionControlManager = FakeSessionControlManager(),
    ): SemantifyrLiveMethodInterceptor {
        return SemantifyrLiveMethodInterceptor(FakeSessionInfoProvider(info), control)
    }

    @Test
    suspend fun `intercepts session info request and replies to the client`() {
        val interceptor = newInterceptor()
        val bridge = RecordingLspBridge()
        val request = LspMessages.request(id = "42", method = SemantifyrLiveMethods.SESSION_INFO)

        val handled = interceptor.interceptClientMessage("", request, bridge)

        assertThat(handled).isTrue()
        val response = bridge.toClient.single() as ResponseMessage
        assertThat(response.id).isEqualTo("42")
        assertThat(response.result).isEqualTo(testSessionInfo)
    }

    @Test
    suspend fun `intercepts inflight list request and replies with the active set`() {
        val control = FakeSessionControlManager().apply {
            inflight = listOf(
                ActiveVerificationInfo(
                    requestId = "42",
                    kind = VerificationKind.Verify,
                    caseLabel = "Foo",
                    portfolioId = "smart-full",
                    elapsed = 3.seconds,
                ),
            )
        }
        val interceptor = newInterceptor(control = control)
        val bridge = RecordingLspBridge()
        val request = LspMessages.request(id = "9", method = SemantifyrLiveMethods.INFLIGHT_LIST)

        val handled = interceptor.interceptClientMessage("", request, bridge)

        assertThat(handled).isTrue()
        val response = bridge.toClient.single() as ResponseMessage
        val payload = response.result as InflightChangedParams
        assertThat(payload.inflight).hasSize(1)
        assertThat(payload.inflight.single().requestId).isEqualTo("42")
        assertThat(payload.inflight.single().kind).isEqualTo(VerificationKind.Verify)
        assertThat(payload.inflight.single().caseLabel).isEqualTo("Foo")
    }

    @Test
    suspend fun `intercepts inflight cancel request, routes to the manager, and returns the boolean outcome`() {
        val control = FakeSessionControlManager().apply {
            inflight = listOf(ActiveVerificationInfo(requestId = "abc"))
        }
        val interceptor = newInterceptor(control = control)
        val bridge = RecordingLspBridge()
        val request = LspMessages.request(
            id = "9",
            method = SemantifyrLiveMethods.INFLIGHT_CANCEL,
            params = InflightCancelParams("abc"),
        )

        val handled = interceptor.interceptClientMessage("", request, bridge)

        assertThat(handled).isTrue()
        assertThat(control.cancelled).containsExactly("abc")
        val response = bridge.toClient.single() as ResponseMessage
        assertThat(response.result).isEqualTo(true)
    }

    @Test
    suspend fun `intercepts inflight cancelAll request and returns the cancelled count`() {
        val control = FakeSessionControlManager().apply {
            inflight = listOf(
                ActiveVerificationInfo(requestId = "a"),
                ActiveVerificationInfo(requestId = "b"),
            )
        }
        val interceptor = newInterceptor(control = control)
        val bridge = RecordingLspBridge()
        val request = LspMessages.request(id = "9", method = SemantifyrLiveMethods.INFLIGHT_CANCEL_ALL)

        val handled = interceptor.interceptClientMessage("", request, bridge)

        assertThat(handled).isTrue()
        assertThat(control.cancelAllCount).isEqualTo(2)
        val response = bridge.toClient.single() as ResponseMessage
        assertThat(response.result).isEqualTo(2)
    }

    @Test
    suspend fun `passes through unrelated requests`() {
        val interceptor = newInterceptor()
        val bridge = RecordingLspBridge()
        val request = LspMessages.request(id = "1", method = "textDocument/completion")

        val handled = interceptor.interceptClientMessage("", request, bridge)

        assertThat(handled).isFalse()
        assertThat(bridge.toClient).isEmpty()
    }

    @Test
    suspend fun `passes through notifications`() {
        val interceptor = newInterceptor()
        val bridge = RecordingLspBridge()
        val notification = LspMessages.notification(method = "textDocument/didChange", params = emptyMap<String, Any>())

        val handled = interceptor.interceptClientMessage("", notification, bridge)

        assertThat(handled).isFalse()
        assertThat(bridge.toClient).isEmpty()
    }

    @Test
    suspend fun `passes through server messages`() {
        val interceptor = newInterceptor()
        val bridge = RecordingLspBridge()
        val message = LspMessages.response(id = "1")

        val handled = interceptor.interceptServerMessage("", message, bridge)

        assertThat(handled).isFalse()
        assertThat(bridge.toClient).isEmpty()
    }
}
