/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.lsp.bridge

import hu.bme.mit.semantifyr.live.backend.server.VerificationKind
import hu.bme.mit.semantifyr.live.backend.testing.FakeSessionVerificationManager
import hu.bme.mit.semantifyr.live.backend.testing.LspMessages
import hu.bme.mit.semantifyr.live.backend.testing.RecordingLspBridge
import hu.bme.mit.semantifyr.live.backend.testing.serialize
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class VerificationMessageInterceptorTest {

    @Test
    suspend fun `unrelated messages pass through without enqueueing`() {
        val host = FakeSessionVerificationManager()
        val interceptor = VerificationMessageInterceptor(host)

        val notification = LspMessages.publishDiagnostics("file:///workspace/snippet.oxsts")
        val result = interceptor.interceptClientMessage(notification.serialize(), notification, RecordingLspBridge())

        assertThat(result).isFalse()
        assertThat(host.enqueued).isEmpty()
    }

    @Test
    suspend fun `verification request is consumed and enqueued with rewritten raw`() {
        val host = FakeSessionVerificationManager()
        val interceptor = VerificationMessageInterceptor(host)
        val request = LspMessages.executeCommand(id = "42", command = "oxsts.case.verify")
        val raw = request.serialize()

        val result = interceptor.interceptClientMessage(raw, request, RecordingLspBridge())

        assertThat(result).isTrue()
        assertThat(host.enqueued).singleElement().isEqualTo(
            FakeSessionVerificationManager.Enqueued("42", raw, VerificationKind.Verify, null, null),
        )
    }

    @Test
    suspend fun `server response for a tracked verification is consumed and completes it`() {
        val host = FakeSessionVerificationManager().apply { tracked = setOf("42") }
        val interceptor = VerificationMessageInterceptor(host)
        val response = LspMessages.response(id = "42", result = mapOf("status" to "passed"))
        val raw = response.serialize()

        val result = interceptor.interceptServerMessage(raw, response, RecordingLspBridge())

        assertThat(result).isTrue()
        assertThat(host.completed).singleElement().isEqualTo(
            FakeSessionVerificationManager.Completed("42", raw),
        )
    }

    @Test
    suspend fun `server response for an unrelated request passes through and is not completed`() {
        val host = FakeSessionVerificationManager()
        val interceptor = VerificationMessageInterceptor(host)
        val response = LspMessages.response(id = "99", result = mapOf("status" to "passed"))

        val result = interceptor.interceptServerMessage(response.serialize(), response, RecordingLspBridge())

        assertThat(result).isFalse()
        assertThat(host.completed).isEmpty()
    }

    @Test
    suspend fun `validate-witness command is also enqueued through the same throttle`() {
        val host = FakeSessionVerificationManager()
        val interceptor = VerificationMessageInterceptor(host)
        val request = LspMessages.executeCommand(id = "v-1", command = "oxsts.case.validateWitness")
        val raw = request.serialize()

        val result = interceptor.interceptClientMessage(raw, request, RecordingLspBridge())

        assertThat(result).isTrue()
        assertThat(host.enqueued).singleElement().isEqualTo(
            FakeSessionVerificationManager.Enqueued("v-1", raw, VerificationKind.Validate, null, null),
        )
    }
}
