/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.lsp

import hu.bme.mit.semantifyr.live.backend.testing.parseLspMessage
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText

class WorkspaceSyncerTest {

    @TempDir
    lateinit var tempDir: Path

    private val clientUri = "file:///workspace/snippet.oxsts"
    private val verificationCommand = "oxsts.case.verify"

    private fun newSync(
        file: Path = tempDir.resolve("snippet.oxsts"),
        verificationCommandOverride: String? = verificationCommand,
    ): Pair<WorkspaceSyncer, Path> {
        file.writeText("")
        return WorkspaceSyncer(
            sessionId = "test-session",
            clientUri = clientUri,
            targetFile = file,
            verificationCommand = verificationCommandOverride,
        ) to file
    }

    private suspend fun WorkspaceSyncer.handle(raw: String) {
        handleOutgoingMessage(parseLspMessage(raw))
    }

    @Test
    suspend fun `didOpen for the session URI writes the full text to disk`() {
        val (sync, file) = newSync()
        sync.handle(
            """{"jsonrpc":"2.0","method":"textDocument/didOpen","params":{"textDocument":{"uri":"$clientUri","languageId":"oxsts","version":1,"text":"package demo\nclass Main { var x: int := 0 }"}}}""",
        )
        assertThat(file.readText()).isEqualTo("package demo\nclass Main { var x: int := 0 }")
    }

    @Test
    suspend fun `full-text didChange replaces the file contents`() {
        val (sync, file) = newSync()
        sync.handle(
            """{"jsonrpc":"2.0","method":"textDocument/didOpen","params":{"textDocument":{"uri":"$clientUri","languageId":"oxsts","version":1,"text":"old"}}}""",
        )
        sync.handle(
            """{"jsonrpc":"2.0","method":"textDocument/didChange","params":{"textDocument":{"uri":"$clientUri","version":2},"contentChanges":[{"text":"new full text"}]}}""",
        )
        assertThat(file.readText()).isEqualTo("new full text")
    }

    @Test
    suspend fun `incremental didChange applies the edit to the in-memory buffer and writes to disk`() {
        val (sync, file) = newSync()
        sync.handle(
            """{"jsonrpc":"2.0","method":"textDocument/didOpen","params":{"textDocument":{"uri":"$clientUri","languageId":"oxsts","version":1,"text":"hello world"}}}""",
        )
        sync.handle(
            """{"jsonrpc":"2.0","method":"textDocument/didChange","params":{"textDocument":{"uri":"$clientUri","version":2},"contentChanges":[
              {"range":{"start":{"line":0,"character":6},"end":{"line":0,"character":11}},"text":"Kotlin"}
            ]}}""",
        )
        assertThat(file.readText()).isEqualTo("hello Kotlin")
    }

    @Test
    suspend fun `multi-line incremental edits apply correctly across line boundaries`() {
        val (sync, file) = newSync()
        sync.handle(
            """{"jsonrpc":"2.0","method":"textDocument/didOpen","params":{"textDocument":{"uri":"$clientUri","languageId":"oxsts","version":1,"text":"line one\nline two\nline three"}}}""",
        )
        sync.handle(
            """{"jsonrpc":"2.0","method":"textDocument/didChange","params":{"textDocument":{"uri":"$clientUri","version":2},"contentChanges":[
              {"range":{"start":{"line":1,"character":5},"end":{"line":2,"character":0}},"text":""}
            ]}}""",
        )
        assertThat(file.readText()).isEqualTo("line one\nline line three")
    }

    @Test
    suspend fun `multiple incremental edits in one didChange apply in order`() {
        val (sync, file) = newSync()
        sync.handle(
            """{"jsonrpc":"2.0","method":"textDocument/didOpen","params":{"textDocument":{"uri":"$clientUri","languageId":"oxsts","version":1,"text":"abcdef"}}}""",
        )
        sync.handle(
            """{"jsonrpc":"2.0","method":"textDocument/didChange","params":{"textDocument":{"uri":"$clientUri","version":2},"contentChanges":[
              {"range":{"start":{"line":0,"character":3},"end":{"line":0,"character":3}},"text":"X"},
              {"range":{"start":{"line":0,"character":5},"end":{"line":0,"character":7}},"text":""}
            ]}}""",
        )
        assertThat(file.readText()).isEqualTo("abcXd")
    }

    @Test
    suspend fun `mixed incremental + full updates within one didChange apply in order`() {
        val (sync, file) = newSync()
        sync.handle(
            """{"jsonrpc":"2.0","method":"textDocument/didOpen","params":{"textDocument":{"uri":"$clientUri","languageId":"oxsts","version":1,"text":"abc"}}}""",
        )
        sync.handle(
            """{"jsonrpc":"2.0","method":"textDocument/didChange","params":{"textDocument":{"uri":"$clientUri","version":2},"contentChanges":[
              {"range":{"start":{"line":0,"character":0},"end":{"line":0,"character":1}},"text":"X"},
              {"text":"final"}
            ]}}""",
        )
        assertThat(file.readText()).isEqualTo("final")
    }

    @Test
    suspend fun `verification executeCommand re-flushes the buffer to disk`() {
        val (sync, file) = newSync()
        sync.handle(
            """{"jsonrpc":"2.0","method":"textDocument/didOpen","params":{"textDocument":{"uri":"$clientUri","languageId":"oxsts","version":1,"text":"buffered content"}}}""",
        )
        file.writeText("STALE")
        assertThat(file.readText()).isEqualTo("STALE")

        sync.handle(
            """{"jsonrpc":"2.0","id":42,"method":"workspace/executeCommand","params":{"command":"oxsts.case.verify","arguments":[{"uri":"$clientUri","range":{"start":{"line":0,"character":0},"end":{"line":0,"character":0}}}]}}""",
        )
        assertThat(file.readText()).isEqualTo("buffered content")
    }

    @Test
    suspend fun `verification pre-flush sees edits applied between didOpen and verification`() {
        val (sync, file) = newSync()
        sync.handle(
            """{"jsonrpc":"2.0","method":"textDocument/didOpen","params":{"textDocument":{"uri":"$clientUri","languageId":"oxsts","version":1,"text":"hello world"}}}""",
        )
        sync.handle(
            """{"jsonrpc":"2.0","method":"textDocument/didChange","params":{"textDocument":{"uri":"$clientUri","version":2},"contentChanges":[
              {"range":{"start":{"line":0,"character":6},"end":{"line":0,"character":11}},"text":"Semantifyr"}
            ]}}""",
        )
        file.writeText("CLOBBERED")
        sync.handle(
            """{"jsonrpc":"2.0","id":1,"method":"workspace/executeCommand","params":{"command":"oxsts.case.verify","arguments":[{"uri":"$clientUri","range":{"start":{"line":0,"character":0},"end":{"line":0,"character":0}}}]}}""",
        )
        assertThat(file.readText()).isEqualTo("hello Semantifyr")
    }

    @Test
    suspend fun `non-verify executeCommands do not re-flush the file`() {
        val (sync, file) = newSync()
        sync.handle(
            """{"jsonrpc":"2.0","method":"textDocument/didOpen","params":{"textDocument":{"uri":"$clientUri","languageId":"oxsts","version":1,"text":"buffered"}}}""",
        )
        file.writeText("untouched")
        sync.handle(
            """{"jsonrpc":"2.0","id":7,"method":"workspace/executeCommand","params":{"command":"oxsts.case.discover","arguments":["$clientUri"]}}""",
        )
        assertThat(file.readText()).isEqualTo("untouched")
    }

    @Test
    suspend fun `flavors without a verify command never re-flush on executeCommand`() {
        val (sync, file) = newSync(verificationCommandOverride = null)
        sync.handle(
            """{"jsonrpc":"2.0","method":"textDocument/didOpen","params":{"textDocument":{"uri":"$clientUri","languageId":"oxsts","version":1,"text":"buffered"}}}""",
        )
        file.writeText("untouched")
        sync.handle(
            """{"jsonrpc":"2.0","id":7,"method":"workspace/executeCommand","params":{"command":"oxsts.case.verify","arguments":[]}}""",
        )
        assertThat(file.readText()).isEqualTo("untouched")
    }

    @Test
    suspend fun `events for a different document URI are ignored`() {
        val (sync, file) = newSync()
        file.writeText("untouched")
        sync.handle(
            """{"jsonrpc":"2.0","method":"textDocument/didOpen","params":{"textDocument":{"uri":"file:///some/other/file.oxsts","languageId":"oxsts","version":1,"text":"unrelated"}}}""",
        )
        assertThat(file.readText()).isEqualTo("untouched")
    }

    @Test
    suspend fun `non-document methods are ignored`() {
        val (sync, file) = newSync()
        file.writeText("untouched")
        sync.handle(
            """{"jsonrpc":"2.0","id":1,"method":"textDocument/completion","params":{}}""",
        )
        assertThat(file.readText()).isEqualTo("untouched")
    }

    @Test
    suspend fun `out-of-range incremental edits are ignored without crashing`() {
        val (sync, file) = newSync()
        sync.handle(
            """{"jsonrpc":"2.0","method":"textDocument/didOpen","params":{"textDocument":{"uri":"$clientUri","languageId":"oxsts","version":1,"text":"abc"}}}""",
        )
        sync.handle(
            """{"jsonrpc":"2.0","method":"textDocument/didChange","params":{"textDocument":{"uri":"$clientUri","version":2},"contentChanges":[
              {"range":{"start":{"line":0,"character":2},"end":{"line":0,"character":1}},"text":"!"}
            ]}}""",
        )
        assertThat(file.readText()).isEqualTo("abc")
    }
}
