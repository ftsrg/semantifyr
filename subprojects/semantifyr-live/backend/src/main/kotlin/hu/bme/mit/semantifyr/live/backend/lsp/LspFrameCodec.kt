/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.lsp

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream

object LspFrameCodec {

    /**
     * Read a single Content-Length–framed LSP message from [input].
     *
     * Blocks until a complete frame is available. Returns `null` on EOF (the LSP
     * process closed its stdout). Throws on malformed headers.
     */
    fun readFrame(input: InputStream): String? {
        val headerText = readHeaderUntilBlankLine(input) ?: return null
        val contentLength = parseContentLength(headerText)
            ?: error("LSP frame missing Content-Length header: ${headerText.trim()}")

        val payload = ByteArray(contentLength)
        var read = 0
        while (read < contentLength) {
            val n = input.read(payload, read, contentLength - read)
            if (n == -1) {
                return null
            }
            read += n
        }
        return String(payload, Charsets.UTF_8)
    }

    private fun readHeaderUntilBlankLine(input: InputStream): String? {
        val headerBytes = ByteArrayOutputStream()
        var state = HeaderState.START
        while (state != HeaderState.DONE) {
            val ch = input.read()
            if (ch == -1) {
                return null
            }
            headerBytes.write(ch)
            state = state.advance(ch)
        }
        return headerBytes.toString(Charsets.US_ASCII)
    }

    private enum class HeaderState {
        START,
        SAW_CR,
        SAW_CRLF,
        SAW_CRLF_CR,
        DONE,
        ;

        fun advance(ch: Int): HeaderState = when (ch) {
            '\r'.code if (this == START || this == SAW_CRLF) -> if (this == START) SAW_CR else SAW_CRLF_CR
            '\n'.code if (this == SAW_CR) -> SAW_CRLF
            '\n'.code if (this == SAW_CRLF_CR) -> DONE
            else -> START
        }
    }

    /**
     * Wrap [json] in a Content-Length header and write it to [output] as a single
     * atomic LSP frame. Thread-safe when callers synchronize on [output].
     */
    fun writeFrame(output: OutputStream, json: String) {
        val bytes = json.toByteArray(Charsets.UTF_8)
        val header = "Content-Length: ${bytes.size}\r\n\r\n".toByteArray(Charsets.US_ASCII)
        output.write(header)
        output.write(bytes)
        output.flush()
    }

    private fun parseContentLength(header: String): Int? {
        for (line in header.split("\r\n")) {
            val colon = line.indexOf(':')
            if (colon < 0) {
                continue
            }
            val name = line.substring(0, colon).trim()
            if (name.equals("Content-Length", ignoreCase = true)) {
                return line.substring(colon + 1).trim().toIntOrNull()
            }
        }
        return null
    }
}
