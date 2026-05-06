/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.lsp.bridge

import hu.bme.mit.semantifyr.live.backend.server.LspProxyInfo
import hu.bme.mit.semantifyr.live.backend.server.SessionInfo
import hu.bme.mit.semantifyr.live.backend.utils.lspMessageHandler
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.lsp4j.ExecuteCommandParams
import org.eclipse.lsp4j.jsonrpc.messages.Message
import org.eclipse.lsp4j.jsonrpc.messages.NotificationMessage
import org.eclipse.lsp4j.jsonrpc.messages.RequestMessage
import org.eclipse.lsp4j.jsonrpc.messages.ResponseMessage
import org.junit.jupiter.api.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class SessionInfoMessageInterceptorTest {

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

    private val host = object : SessionInfoProvider {
        override fun getSessionInfo(): SessionInfo = testSessionInfo
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
    fun `intercepts semantifyr session info command and sends response to client`() = runTest {
        val interceptor = SessionInfoMessageInterceptor(host)
        val bridge = CapturingBridge()

        val request = request(id = "42", command = "semantifyr.session.info")
        val result = interceptor.interceptClientMessage(serialize(request), request, bridge)

        assertThat(result).isTrue()
        assertThat(bridge.sentToClient).singleElement().isInstanceOfSatisfying(ResponseMessage::class.java) {
            assertThat(it.id).isEqualTo("42")
            assertThat(it.result).isNotNull
        }
    }

    @Test
    fun `passes through unrelated commands`() = runTest {
        val interceptor = SessionInfoMessageInterceptor(host)
        val bridge = CapturingBridge()

        val request = request(id = "1", command = "oxsts.case.verify")
        val result = interceptor.interceptClientMessage(serialize(request), request, bridge)

        assertThat(result).isFalse()
        assertThat(bridge.sentToClient).isEmpty()
    }

    @Test
    fun `passes through notifications`() = runTest {
        val interceptor = SessionInfoMessageInterceptor(host)
        val bridge = CapturingBridge()

        val notification = NotificationMessage().apply {
            method = "textDocument/didChange"
            params = emptyMap<String, Any>()
        }
        val result = interceptor.interceptClientMessage(serialize(notification), notification, bridge)

        assertThat(result).isFalse()
        assertThat(bridge.sentToClient).isEmpty()
    }

    @Test
    fun `passes through server messages`() = runTest {
        val interceptor = SessionInfoMessageInterceptor(host)
        val bridge = CapturingBridge()

        val message: Message = ResponseMessage().apply { id = "1" }
        val result = interceptor.interceptServerMessage(serialize(message), message, bridge)

        assertThat(result).isFalse()
        assertThat(bridge.sentToClient).isEmpty()
    }

    private fun request(id: String, command: String) = RequestMessage().apply {
        this.id = id
        this.method = "workspace/executeCommand"
        this.params = ExecuteCommandParams(command, emptyList())
    }

    private fun serialize(message: Message): String = lspMessageHandler.serialize(message)
}
