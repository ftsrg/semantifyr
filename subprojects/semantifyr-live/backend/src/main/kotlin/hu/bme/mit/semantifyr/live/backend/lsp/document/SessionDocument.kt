/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.lsp.document

import hu.bme.mit.semantifyr.logging.loggerFactory
import hu.bme.mit.semantifyr.logging.warn
import org.eclipse.emf.common.util.URI
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.TextDocumentContentChangeEvent
import org.eclipse.xtext.ide.server.Document
import org.eclipse.xtext.resource.XtextResource
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

class SessionDocument(
    val uri: String,
    val emfUri: URI,
    val resource: XtextResource,
    val onDiskPath: Path,
    initialText: String,
) {

    private val logger by loggerFactory()

    @Volatile
    private var currentText = initialText

    init {
        loadResourceFromText(initialText)
    }

    fun text(): String {
        return currentText
    }

    fun xtextDocument(): Document {
        return Document(0, currentText)
    }

    fun applyChanges(changes: List<TextDocumentContentChangeEvent>) {
        if (changes.isEmpty()) {
            return
        }
        val newText = changes.fold(currentText) { acc, change ->
            val range = change.range
            if (range == null) {
                change.text
            } else {
                applyRange(acc, range.start, range.end, change.text)
            }
        }
        currentText = newText
        loadResourceFromText(newText)
    }

    fun flushToDisk() {
        try {
            Files.writeString(onDiskPath, currentText)
        } catch (e: Throwable) {
            logger.warn(e) { "Failed to flush document to disk uri=$uri path=$onDiskPath" }
        }
    }

    fun unload() {
        try {
            resource.unload()
        } catch (e: Throwable) {
            logger.warn(e) { "Failed to unload resource uri=$uri" }
        }
    }

    private fun loadResourceFromText(text: String) {
        resource.unload()
        ByteArrayInputStream(text.toByteArray(StandardCharsets.UTF_8)).use {
            resource.load(it, emptyMap<Any, Any>())
        }
    }

    private fun applyRange(
        text: String,
        start: Position,
        end: Position,
        replacement: String,
    ): String {
        val startOffset = offsetOf(text, start.line, start.character)
        val endOffset = offsetOf(text, end.line, end.character)
        return text.substring(0, startOffset) + replacement + text.substring(endOffset)
    }

    private fun offsetOf(
        text: String,
        line: Int,
        character: Int,
    ): Int {
        var offset = 0
        var currentLine = 0
        while (currentLine < line && offset < text.length) {
            val nl = text.indexOf('\n', offset)
            if (nl < 0) {
                return text.length
            }
            offset = nl + 1
            currentLine++
        }
        return (offset + character).coerceAtMost(text.length)
    }
}
