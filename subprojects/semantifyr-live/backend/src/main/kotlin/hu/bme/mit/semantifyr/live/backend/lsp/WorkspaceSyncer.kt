/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.lsp

import hu.bme.mit.semantifyr.logging.debug
import hu.bme.mit.semantifyr.logging.error
import hu.bme.mit.semantifyr.logging.loggerFactory
import hu.bme.mit.semantifyr.logging.warn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.ExecuteCommandParams
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.TextDocumentContentChangeEvent
import org.eclipse.lsp4j.jsonrpc.messages.Message
import org.eclipse.lsp4j.jsonrpc.messages.NotificationMessage
import org.eclipse.lsp4j.jsonrpc.messages.RequestMessage
import java.nio.file.Path
import kotlin.io.path.writeText

class WorkspaceSyncer(
    private val sessionId: String,
    private val clientUri: String,
    private val targetFile: Path,
    private val verificationCommand: String? = null,
) {
    private val logger by loggerFactory()

    private var currentText: String = ""

    suspend fun handleOutgoingMessage(message: Message) {
        when (message) {
            is NotificationMessage -> handleNotification(message)
            is RequestMessage -> handleRequest(message)
        }
    }

    private suspend fun handleNotification(msg: NotificationMessage) {
        when (val params = msg.params) {
            is DidOpenTextDocumentParams -> handleDidOpen(params)
            is DidChangeTextDocumentParams -> handleDidChange(params)
        }
    }

    private suspend fun handleRequest(msg: RequestMessage) {
        val params = msg.params
        if (params is ExecuteCommandParams) {
            handleExecuteCommand(params)
        }
    }

    private suspend fun handleDidOpen(params: DidOpenTextDocumentParams) {
        if (!params.textDocument.uri.startsWith(clientUri)) {
            return
        }
        currentText = params.textDocument.text
        writeFileSafely("didOpen")
    }

    private suspend fun handleDidChange(params: DidChangeTextDocumentParams) {
        if (!params.textDocument.uri.startsWith(clientUri)) {
            return
        }
        for (change in params.contentChanges) {
            currentText = if (change.range == null) {
                change.text
            } else {
                applyIncrementalChange(currentText, change)
            }
        }
        writeFileSafely("didChange")
    }

    private suspend fun handleExecuteCommand(params: ExecuteCommandParams) {
        val verifyCmd = verificationCommand ?: return
        if (params.command != verifyCmd) {
            return
        }
        writeFileSafely("verification pre-flush")
    }

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
        if (line < 0) {
            return 0
        }
        var currentLine = 0
        var i = 0
        while (i < text.length && currentLine < line) {
            if (text[i] == '\n') {
                currentLine++
            }
            i++
        }
        return (i + character).coerceAtMost(text.length)
    }

    private suspend fun writeFileSafely(source: String) {
        try {
            runInterruptible(Dispatchers.IO) {
                targetFile.writeText(currentText)
            }
            logger.debug { "Session=$sessionId synced ${currentText.length} bytes from $source to $targetFile" }
        } catch (t: Throwable) {
            logger.error { "Session=$sessionId failed to sync $source buffer to $targetFile: $t" }
        }
    }

}
