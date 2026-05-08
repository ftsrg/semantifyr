/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.lsp.bridge

import hu.bme.mit.semantifyr.live.backend.lsp.WorkspaceSyncer
import hu.bme.mit.semantifyr.live.backend.testing.LspMessages
import hu.bme.mit.semantifyr.live.backend.testing.RecordingLspBridge
import hu.bme.mit.semantifyr.live.backend.testing.serialize
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText

class WorkspaceSyncerInterceptorTest {

    @TempDir
    lateinit var tempDir: Path

    private fun syncerFor(targetFile: Path) = WorkspaceSyncer(
        sessionId = "s1",
        clientUri = "file:///workspace/",
        targetFile = targetFile,
    )

    @Test
    suspend fun `delegates client notifications to the syncer and never consumes`() {
        val targetFile = tempDir.resolve("snippet.oxsts").apply { writeText("") }
        val interceptor = WorkspaceSyncerInterceptor(syncerFor(targetFile))

        val didOpen = LspMessages.didOpen(uri = "file:///workspace/snippet.oxsts", text = "hello")
        val result = interceptor.interceptClientMessage(didOpen.serialize(), didOpen, RecordingLspBridge())

        assertThat(result).isFalse() // never consumes
        assertThat(targetFile.readText()).isEqualTo("hello")
    }

    @Test
    suspend fun `server messages pass through untouched`() {
        val targetFile = tempDir.resolve("snippet.oxsts").apply { writeText("untouched") }
        val interceptor = WorkspaceSyncerInterceptor(syncerFor(targetFile))

        val response = LspMessages.response(id = "1")
        val result = interceptor.interceptServerMessage(response.serialize(), response, RecordingLspBridge())

        assertThat(result).isFalse()
        assertThat(targetFile.readText()).isEqualTo("untouched")
    }
}
