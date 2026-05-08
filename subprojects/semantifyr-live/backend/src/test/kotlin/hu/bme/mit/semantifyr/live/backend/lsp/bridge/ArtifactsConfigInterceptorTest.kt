/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.lsp.bridge

import com.google.gson.JsonObject
import hu.bme.mit.semantifyr.live.backend.testing.LspMessages
import hu.bme.mit.semantifyr.live.backend.testing.RecordingLspBridge
import hu.bme.mit.semantifyr.live.backend.testing.testSessionContext
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.lsp4j.jsonrpc.messages.NotificationMessage
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class ArtifactsConfigInterceptorTest {

    @Test
    suspend fun `initialized notification triggers a didChangeConfiguration with artifacts directory`(
        @TempDir workspace: Path,
    ) {
        val interceptor = ArtifactsConfigInterceptor(testSessionContext(workspace))
        val bridge = RecordingLspBridge()
        val initialized = LspMessages.notification(method = "initialized", params = JsonObject())

        val consumed = interceptor.interceptClientMessage("{}", initialized, bridge)

        assertThat(consumed).isFalse() // original 'initialized' still forwarded normally
        assertThat(bridge.toServer).hasSize(1)
        val sent = bridge.toServer.single() as NotificationMessage
        assertThat(sent.method).isEqualTo("workspace/didChangeConfiguration")
        val params = sent.params as JsonObject
        val settings = params.getAsJsonObject("settings").getAsJsonObject("semantifyr")
        assertThat(settings.get("artifacts.location").asString).isEqualTo("directory")
        assertThat(settings.get("artifacts.directory").asString)
            .isEqualTo(workspace.resolve("artifacts").toAbsolutePath().toString())
    }

    @Test
    suspend fun `subsequent client messages do not re-emit the configuration notification`(
        @TempDir workspace: Path,
    ) {
        val interceptor = ArtifactsConfigInterceptor(testSessionContext(workspace))
        val bridge = RecordingLspBridge()
        val initialized = LspMessages.notification(method = "initialized", params = JsonObject())
        val later = LspMessages.notification(method = "initialized", params = JsonObject())

        interceptor.interceptClientMessage("{}", initialized, bridge)
        interceptor.interceptClientMessage("{}", later, bridge)

        assertThat(bridge.toServer).hasSize(1)
    }

    @Test
    suspend fun `non-initialized client messages are passed through unchanged`(
        @TempDir workspace: Path,
    ) {
        val interceptor = ArtifactsConfigInterceptor(testSessionContext(workspace))
        val bridge = RecordingLspBridge()
        val request = LspMessages.request(id = "1", method = "textDocument/didOpen")

        val consumed = interceptor.interceptClientMessage("{}", request, bridge)

        assertThat(consumed).isFalse()
        assertThat(bridge.toServer).isEmpty()
    }
}
