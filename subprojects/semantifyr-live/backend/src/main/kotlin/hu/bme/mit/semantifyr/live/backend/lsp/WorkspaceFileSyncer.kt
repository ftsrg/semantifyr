/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.lsp

import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.ExecuteCommandParams
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.TextDocumentContentChangeEvent
import org.eclipse.lsp4j.jsonrpc.messages.Message
import org.eclipse.lsp4j.jsonrpc.messages.NotificationMessage
import org.eclipse.lsp4j.jsonrpc.messages.RequestMessage
import hu.bme.mit.semantifyr.live.backend.utils.debug
import hu.bme.mit.semantifyr.live.backend.utils.error
import hu.bme.mit.semantifyr.live.backend.utils.loggerFactory
import hu.bme.mit.semantifyr.live.backend.utils.warn
import java.nio.file.Path
import kotlin.io.path.writeText

class WorkspaceFileSyncer(
    private val sessionId: String,
    private val clientUri: String,
    private val targetFile: Path,
    private val verifyCommand: String? = null,
) {
    private val logger by loggerFactory()

    private var currentText: String = ""

    fun handleOutgoingMessage(message: Message) {
        when (message) {
            is NotificationMessage -> handleNotification(message)
            is RequestMessage -> handleRequest(message)
        }
    }

    private fun handleNotification(msg: NotificationMessage) {
        when (val params = msg.params) {
            is DidOpenTextDocumentParams -> handleDidOpen(params)
            is DidChangeTextDocumentParams -> handleDidChange(params)
        }
    }

    private fun handleRequest(msg: RequestMessage) {
        val params = msg.params
        if (params is ExecuteCommandParams) {
            handleExecuteCommand(params)
        }
    }

    private fun handleDidOpen(params: DidOpenTextDocumentParams) {
        if (params.textDocument.uri != clientUri) return
        currentText = params.textDocument.text
        writeFileSafely("didOpen")
    }

    private fun handleDidChange(params: DidChangeTextDocumentParams) {
        if (params.textDocument.uri != clientUri) return
        for (change in params.contentChanges) {
            currentText = if (change.range == null) {
                change.text
            } else {
                applyIncrementalChange(currentText, change)
            }
        }
        writeFileSafely("didChange")
    }

    /**
     * Verify-time backstop: re-flush the buffer to disk before the verify command
     * so the LSP's file-system read path always sees the latest content.
     */
    private fun handleExecuteCommand(params: ExecuteCommandParams) {
        val verifyCmd = verifyCommand ?: return
        if (params.command != verifyCmd) return
        writeFileSafely("verify pre-flush")
    }

    /**
     * Apply a single LSP incremental content-change event to [current].
     *
     * The LSP spec describes positions as `(line, character)` over UTF-16 code
     * units. For oxsts/xsts/gamma source (essentially ASCII) the distinction
     * doesn't matter.
     */
    private fun applyIncrementalChange(
        current: String,
        change: TextDocumentContentChangeEvent,
    ): String {
        val range = change.range ?: return change.text
        val startOffset = positionToOffset(current, range.start)
        val endOffset = positionToOffset(current, range.end)
        if (startOffset > endOffset || endOffset > current.length) {
            logger.warn { "Session=$sessionId ignoring out-of-range incremental change: start=$startOffset end=$endOffset len=${current.length}" }
            return current
        }
        return current.substring(0, startOffset) + change.text + current.substring(endOffset)
    }

    private fun positionToOffset(text: String, position: Position): Int {
        val line = position.line
        val character = position.character
        if (line < 0) return 0
        var currentLine = 0
        var i = 0
        while (i < text.length && currentLine < line) {
            if (text[i] == '\n') currentLine++
            i++
        }
        return (i + character).coerceAtMost(text.length)
    }

    private fun writeFileSafely(source: String) {
        try {
            targetFile.writeText(currentText)
            logger.debug { "Session=$sessionId synced ${currentText.length} bytes from $source to $targetFile" }
        } catch (t: Throwable) {
            logger.error { "Session=$sessionId failed to sync $source buffer to $targetFile: $t" }
        }
    }

}
