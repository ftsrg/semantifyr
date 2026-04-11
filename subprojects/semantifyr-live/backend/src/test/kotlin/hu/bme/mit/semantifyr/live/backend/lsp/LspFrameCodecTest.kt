/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.lsp

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class LspFrameCodecTest {

    private fun lspFrame(payload: String): ByteArray {
        val bytes = payload.toByteArray(Charsets.UTF_8)
        return "Content-Length: ${bytes.size}\r\n\r\n".toByteArray(Charsets.US_ASCII) + bytes
    }

    @Test
    fun `readFrame parses a single Content-Length framed message`() {
        val payload = """{"jsonrpc":"2.0","id":1,"method":"initialize"}"""
        val input = ByteArrayInputStream(lspFrame(payload))

        val result = LspFrameCodec.readFrame(input)
        Assertions.assertThat(result).isEqualTo(payload)
    }

    @Test
    fun `readFrame returns null on EOF`() {
        val input = ByteArrayInputStream(ByteArray(0))
        Assertions.assertThat(LspFrameCodec.readFrame(input)).isNull()
    }

    @Test
    fun `readFrame handles multiple sequential frames`() {
        val first = """{"jsonrpc":"2.0","id":1,"result":{}}"""
        val second = """{"jsonrpc":"2.0","method":"window/logMessage","params":{"type":3,"message":"hi"}}"""
        val input = ByteArrayInputStream(lspFrame(first) + lspFrame(second))

        Assertions.assertThat(LspFrameCodec.readFrame(input)).isEqualTo(first)
        Assertions.assertThat(LspFrameCodec.readFrame(input)).isEqualTo(second)
        Assertions.assertThat(LspFrameCodec.readFrame(input)).isNull()
    }

    @Test
    fun `writeFrame produces valid Content-Length framed output`() {
        val payload = """{"jsonrpc":"2.0","id":2,"method":"shutdown"}"""
        val output = ByteArrayOutputStream()

        LspFrameCodec.writeFrame(output, payload)

        val written = output.toString(Charsets.UTF_8)
        val expectedLen = payload.toByteArray(Charsets.UTF_8).size
        Assertions.assertThat(written).startsWith("Content-Length: $expectedLen\r\n\r\n")
        Assertions.assertThat(written).endsWith(payload)
    }

    @Test
    fun `round-trip preserves the payload`() {
        val payload = """{"jsonrpc":"2.0","params":{"text":"hello\nworld"}}"""
        val output = ByteArrayOutputStream()
        LspFrameCodec.writeFrame(output, payload)

        val input = ByteArrayInputStream(output.toByteArray())
        Assertions.assertThat(LspFrameCodec.readFrame(input)).isEqualTo(payload)
    }
}
