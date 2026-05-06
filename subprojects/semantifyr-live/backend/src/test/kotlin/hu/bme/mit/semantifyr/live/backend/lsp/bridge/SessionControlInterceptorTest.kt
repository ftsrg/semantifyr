/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.lsp.bridge

import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import hu.bme.mit.semantifyr.live.backend.server.ActiveVerificationInfo
import hu.bme.mit.semantifyr.live.backend.server.VerificationKind
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.lsp4j.jsonrpc.messages.Message
import org.eclipse.lsp4j.jsonrpc.messages.NotificationMessage
import org.eclipse.lsp4j.jsonrpc.messages.RequestMessage
import org.eclipse.lsp4j.jsonrpc.messages.ResponseMessage
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

class SessionControlInterceptorTest {

    private class FakeSessionControlManager : SessionControlManager {
        var inflight: List<ActiveVerificationInfo> = emptyList()
        val cancelled = mutableListOf<String>()
        var cancelAllCount: Int = 0

        override fun listInFlight(): List<ActiveVerificationInfo> = inflight
        override suspend fun cancelInFlight(requestId: String): Boolean {
            cancelled += requestId
            return inflight.any { it.requestId == requestId }
        }
        override suspend fun cancelAllInFlight(): Int {
            cancelAllCount = inflight.size
            return cancelAllCount
        }
    }

    private class CapturingBridge : LspBridge {
        val sentToClient = mutableListOf<Message>()
        override suspend fun sendToLspServer(message: Message) = Unit
        override suspend fun sendToLspServer(raw: String) = Unit
        override suspend fun sendToLspClient(message: Message) {
            sentToClient += message
        }
        override suspend fun sendToLspClient(raw: String) = Unit
        override fun recordError() = Unit
    }

    @Test
    fun `unrelated methods pass through unmodified`() = runTest {
        val host = FakeSessionControlManager()
        val interceptor = SessionControlInterceptor(host)
        val request = RequestMessage().apply {
            id = "1"
            method = "textDocument/completion"
        }

        val handled = interceptor.interceptClientMessage("", request, CapturingBridge())

        assertThat(handled).isFalse()
        assertThat(host.cancelled).isEmpty()
    }

    @Test
    fun `inflight list returns current entries to the client`() = runTest {
        val host = FakeSessionControlManager().apply {
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
        val interceptor = SessionControlInterceptor(host)
        val bridge = CapturingBridge()
        val request = RequestMessage().apply {
            id = "9"
            method = INFLIGHT_LIST_METHOD
        }

        val handled = interceptor.interceptClientMessage("", request, bridge)

        assertThat(handled).isTrue()
        assertThat(bridge.sentToClient).hasSize(1)
        val response = bridge.sentToClient.single() as ResponseMessage
        val body = response.result as JsonObject
        val first = body.getAsJsonArray("inflight").get(0).asJsonObject
        assertThat(first.get("requestId").asString).isEqualTo("42")
        assertThat(first.get("kind").asString).isEqualTo("Verify")
        assertThat(first.get("caseLabel").asString).isEqualTo("Foo")
    }

    @Test
    fun `inflight cancel routes to the manager and replies with the boolean outcome`() = runTest {
        val host = FakeSessionControlManager().apply {
            inflight = listOf(ActiveVerificationInfo(requestId = "abc"))
        }
        val interceptor = SessionControlInterceptor(host)
        val bridge = CapturingBridge()
        val request = RequestMessage().apply {
            id = "9"
            method = INFLIGHT_CANCEL_METHOD
            params = JsonObject().apply { add("requestId", JsonPrimitive("abc")) }
        }

        val handled = interceptor.interceptClientMessage("", request, bridge)

        assertThat(handled).isTrue()
        assertThat(host.cancelled).containsExactly("abc")
        val response = bridge.sentToClient.single() as ResponseMessage
        assertThat(response.result.toString()).isEqualTo("true")
    }

    @Test
    fun `inflight cancelAll returns the cancelled count`() = runTest {
        val host = FakeSessionControlManager().apply {
            inflight = listOf(
                ActiveVerificationInfo(requestId = "a"),
                ActiveVerificationInfo(requestId = "b"),
            )
        }
        val interceptor = SessionControlInterceptor(host)
        val bridge = CapturingBridge()
        val request = RequestMessage().apply {
            id = "9"
            method = INFLIGHT_CANCEL_ALL_METHOD
        }

        val handled = interceptor.interceptClientMessage("", request, bridge)

        assertThat(handled).isTrue()
        assertThat(host.cancelAllCount).isEqualTo(2)
        val response = bridge.sentToClient.single() as ResponseMessage
        assertThat(response.result.toString()).isEqualTo("2")
    }

    @Test
    fun `notifications under semantifyr live prefix are not consumed by this interceptor`() = runTest {
        val host = FakeSessionControlManager()
        val interceptor = SessionControlInterceptor(host)
        val notification = NotificationMessage().apply {
            method = INFLIGHT_CHANGED_NOTIFICATION
        }

        val handled = interceptor.interceptClientMessage("", notification, CapturingBridge())

        // Only RequestMessages are routed; notifications fall through (the interceptor itself only
        // emits inflight/changed *to* the client, never receives them from it).
        assertThat(handled).isFalse()
    }
}
