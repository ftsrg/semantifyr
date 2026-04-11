/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.lsp

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test

class UriRewriterTest {

    private val clientUri = "file:///workspace/snippet.oxsts"
    private val serverUri = "file:///tmp/sessions/abc123/snippet.oxsts"
    private val rewriter = UriRewriter(clientUri = clientUri, serverUri = serverUri)

    @Test
    fun `clientToServer replaces client URI with server URI`() {
        val message = """{"params":{"textDocument":{"uri":"$clientUri"}}}"""
        val rewritten = rewriter.clientToServer(message)
        Assertions.assertThat(rewritten).contains(serverUri)
        Assertions.assertThat(rewritten).doesNotContain(clientUri)
    }

    @Test
    fun `serverToClient replaces server URI with client URI`() {
        val message = """{"method":"textDocument/publishDiagnostics","params":{"uri":"$serverUri","diagnostics":[]}}"""
        val rewritten = rewriter.serverToClient(message)
        Assertions.assertThat(rewritten).contains(clientUri)
        Assertions.assertThat(rewritten).doesNotContain(serverUri)
    }

    @Test
    fun `round-trip preserves the original message`() {
        val message = """{"params":{"uri":"$clientUri"}}"""
        val roundTripped = rewriter.serverToClient(rewriter.clientToServer(message))
        Assertions.assertThat(roundTripped).isEqualTo(message)
    }

    @Test
    fun `messages without URIs are unchanged`() {
        val message = """{"jsonrpc":"2.0","id":1,"method":"shutdown"}"""
        Assertions.assertThat(rewriter.clientToServer(message)).isEqualTo(message)
        Assertions.assertThat(rewriter.serverToClient(message)).isEqualTo(message)
    }

    @Test
    fun `multiple occurrences are all replaced`() {
        val message = """{"uri":"$clientUri","related":"$clientUri"}"""
        val rewritten = rewriter.clientToServer(message)
        Assertions.assertThat(rewritten).isEqualTo("""{"uri":"$serverUri","related":"$serverUri"}""")
    }
}
