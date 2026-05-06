/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.lsp.bridge

import hu.bme.mit.semantifyr.live.backend.lsp.WorkspaceSyncer
import hu.bme.mit.semantifyr.live.backend.utils.lspMessageHandler
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.TextDocumentItem
import org.eclipse.lsp4j.jsonrpc.messages.Message
import org.eclipse.lsp4j.jsonrpc.messages.NotificationMessage
import org.eclipse.lsp4j.jsonrpc.messages.ResponseMessage
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText

class WorkspaceSyncerInterceptorTest {

    @TempDir
    lateinit var tempDir: Path

    private object NoOpBridge : LspBridge {
        override suspend fun sendToLspServer(message: Message) = Unit
        override suspend fun sendToLspServer(raw: String) = Unit
        override suspend fun sendToLspClient(message: Message) = Unit
        override suspend fun sendToLspClient(raw: String) = Unit
        override fun recordError() = Unit
    }

    private fun syncerFor(targetFile: Path) = WorkspaceSyncer(
        sessionId = "s1",
        clientUri = "file:///workspace/",
        targetFile = targetFile,
    )

    @Test
    fun `delegates client notifications to the syncer and never consumes`() = runTest {
        val targetFile = tempDir.resolve("snippet.oxsts").apply { writeText("") }
        val interceptor = WorkspaceSyncerInterceptor(syncerFor(targetFile))

        val didOpen = didOpenNotification(uri = "file:///workspace/snippet.oxsts", text = "hello")
        val result = interceptor.interceptClientMessage(serialize(didOpen), didOpen, NoOpBridge)

        assertThat(result).isFalse() // never consumes
        assertThat(targetFile.readText()).isEqualTo("hello")
    }

    @Test
    fun `server messages pass through untouched`() = runTest {
        val targetFile = tempDir.resolve("snippet.oxsts").apply { writeText("untouched") }
        val interceptor = WorkspaceSyncerInterceptor(syncerFor(targetFile))

        val response: Message = ResponseMessage().apply { id = "1" }
        val result = interceptor.interceptServerMessage(serialize(response), response, NoOpBridge)

        assertThat(result).isFalse()
        assertThat(targetFile.readText()).isEqualTo("untouched")
    }

    private fun didOpenNotification(uri: String, text: String) = NotificationMessage().apply {
        method = "textDocument/didOpen"
        params = DidOpenTextDocumentParams(
            TextDocumentItem().apply {
                this.uri = uri
                languageId = "oxsts"
                version = 1
                this.text = text
            },
        )
    }

    private fun serialize(message: Message): String = lspMessageHandler.serialize(message)
}
