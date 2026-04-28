/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.lsp.bridge

import hu.bme.mit.semantifyr.live.backend.utils.lspMessageHandler
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.lsp4j.ExecuteCommandParams
import org.eclipse.lsp4j.jsonrpc.messages.Message
import org.eclipse.lsp4j.jsonrpc.messages.NotificationMessage
import org.eclipse.lsp4j.jsonrpc.messages.RequestMessage
import org.eclipse.lsp4j.jsonrpc.messages.ResponseMessage
import org.junit.jupiter.api.Test

class VerificationMessageInterceptorTest {

    private class FakeSessionVerificationManager : SessionVerificationManager {
        data class Enqueued(val requestId: String, val requestMessage: String)
        data class Completed(val requestId: String, val responseMessage: String)

        val enqueued = mutableListOf<Enqueued>()
        val completed = mutableListOf<Completed>()
        val cancelled = mutableListOf<String>()
        var tracked: Set<String> = emptySet()

        override suspend fun enqueueVerification(requestId: String, requestMessage: String) {
            enqueued += Enqueued(requestId, requestMessage)
        }

        override fun isVerificationTracked(requestId: String): Boolean = requestId in tracked

        override suspend fun completeVerification(requestId: String, responseMessage: String) {
            completed += Completed(requestId, responseMessage)
        }

        override suspend fun cancelVerification(requestId: String) {
            cancelled += requestId
        }
    }

    private object NoOpBridge : LspBridge {
        override suspend fun sendToLspServer(message: Message) = Unit
        override suspend fun sendToLspServer(raw: String) = Unit
        override suspend fun sendToLspClient(message: Message) = Unit
        override suspend fun sendToLspClient(raw: String) = Unit
        override fun recordError() = Unit
    }

    @Test
    fun `unrelated messages pass through without enqueueing`() = runTest {
        val host = FakeSessionVerificationManager()
        val interceptor = VerificationMessageInterceptor(host)

        val notification = notification()
        val result = interceptor.handleClientMessage(serialize(notification), notification, NoOpBridge)

        assertThat(result).isTrue()
        assertThat(host.enqueued).isEmpty()
    }

    @Test
    fun `verification request is consumed and enqueued with rewritten raw`() = runTest {
        val host = FakeSessionVerificationManager()
        val interceptor = VerificationMessageInterceptor(host)
        val request = verificationRequest("42")
        val raw = serialize(request)

        val result = interceptor.handleClientMessage(raw, request, NoOpBridge)

        assertThat(result).isFalse()
        assertThat(host.enqueued).singleElement().isEqualTo(
            FakeSessionVerificationManager.Enqueued("42", raw),
        )
    }

    @Test
    fun `server response for a tracked verification is consumed and completes it`() = runTest {
        val host = FakeSessionVerificationManager().apply { tracked = setOf("42") }
        val interceptor = VerificationMessageInterceptor(host)
        val response = response("42")
        val raw = serialize(response)

        val result = interceptor.handleServerMessage(raw, response, NoOpBridge)

        assertThat(result).isFalse()
        assertThat(host.completed).singleElement().isEqualTo(
            FakeSessionVerificationManager.Completed("42", raw),
        )
    }

    @Test
    fun `server response for an unrelated request passes through and is not completed`() = runTest {
        val host = FakeSessionVerificationManager()
        val interceptor = VerificationMessageInterceptor(host)
        val response = response("99")

        val result = interceptor.handleServerMessage(serialize(response), response, NoOpBridge)

        assertThat(result).isTrue()
        assertThat(host.completed).isEmpty()
    }

    private fun verificationRequest(id: String): RequestMessage = RequestMessage().apply {
        this.id = id
        this.method = "workspace/executeCommand"
        this.params = ExecuteCommandParams("oxsts.case.verify", emptyList())
    }

    private fun notification(): NotificationMessage = NotificationMessage().apply {
        method = "textDocument/publishDiagnostics"
        params = emptyMap<String, Any>()
    }

    private fun response(id: String): ResponseMessage = ResponseMessage().apply {
        this.id = id
        this.result = mapOf("status" to "passed")
    }

    private fun serialize(message: Message): String = lspMessageHandler.serialize(message)
}
