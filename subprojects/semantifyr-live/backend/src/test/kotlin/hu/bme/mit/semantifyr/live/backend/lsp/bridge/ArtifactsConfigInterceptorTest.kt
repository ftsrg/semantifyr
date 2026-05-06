/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.lsp.bridge

import com.google.gson.JsonObject
import hu.bme.mit.semantifyr.live.backend.server.Flavor
import hu.bme.mit.semantifyr.live.backend.server.WorkspaceLayout
import hu.bme.mit.semantifyr.live.backend.session.SessionContext
import io.ktor.websocket.WebSocketSession
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.lsp4j.jsonrpc.messages.Message
import org.eclipse.lsp4j.jsonrpc.messages.NotificationMessage
import org.eclipse.lsp4j.jsonrpc.messages.RequestMessage
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.mock
import java.nio.file.Path
import kotlin.io.path.Path as asPath

class ArtifactsConfigInterceptorTest {

    private fun contextFor(workspace: Path): SessionContext {
        return SessionContext(
            sessionId = "test-session",
            remoteIp = "127.0.0.1",
            flavor = Flavor(
                id = "oxsts",
                displayName = "Semantifyr",
                binaryRelativePath = asPath("does-not-exist"),
                fileName = "snippet.oxsts",
                languageId = "oxsts",
                workspaceLayout = WorkspaceLayout.SingleFile,
                verificationCommand = "oxsts.case.verify",
                discoveryCommand = "oxsts.case.discover",
            ),
            webSocketSession = mock<WebSocketSession>(),
            workingDirectoryPath = workspace,
        )
    }

    private class CapturingBridge : LspBridge {
        val toServer = mutableListOf<Message>()
        val toServerRaw = mutableListOf<String>()

        override suspend fun sendToLspServer(message: Message) {
            toServer.add(message)
        }
        override suspend fun sendToLspServer(raw: String) {
            toServerRaw.add(raw)
        }
        override suspend fun sendToLspClient(message: Message) {}
        override suspend fun sendToLspClient(raw: String) {}
        override fun recordError() {}
    }

    @Test
    fun `initialized notification triggers a didChangeConfiguration with artifacts directory`(
        @TempDir workspace: Path,
    ) = runTest {
        val interceptor = ArtifactsConfigInterceptor(contextFor(workspace))
        val bridge = CapturingBridge()
        val initialized = NotificationMessage().apply {
            method = "initialized"
            params = JsonObject()
        }

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
    fun `subsequent client messages do not re-emit the configuration notification`(
        @TempDir workspace: Path,
    ) = runTest {
        val interceptor = ArtifactsConfigInterceptor(contextFor(workspace))
        val bridge = CapturingBridge()
        val initialized = NotificationMessage().apply {
            method = "initialized"
            params = JsonObject()
        }
        val later = NotificationMessage().apply {
            method = "initialized"
            params = JsonObject()
        }

        interceptor.interceptClientMessage("{}", initialized, bridge)
        interceptor.interceptClientMessage("{}", later, bridge)

        assertThat(bridge.toServer).hasSize(1)
    }

    @Test
    fun `non-initialized client messages are passed through unchanged`(
        @TempDir workspace: Path,
    ) = runTest {
        val interceptor = ArtifactsConfigInterceptor(contextFor(workspace))
        val bridge = CapturingBridge()
        val request = RequestMessage().apply { method = "textDocument/didOpen" }

        val consumed = interceptor.interceptClientMessage("{}", request, bridge)

        assertThat(consumed).isFalse()
        assertThat(bridge.toServer).isEmpty()
    }
}
