/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.lsp.service

import hu.bme.mit.semantifyr.live.backend.exceptions.WorkspaceUriException
import hu.bme.mit.semantifyr.live.backend.lsp.document.SessionDocument
import hu.bme.mit.semantifyr.live.backend.lsp.document.SessionDocumentManager
import hu.bme.mit.semantifyr.live.backend.lsp.language.LanguageServices
import hu.bme.mit.semantifyr.live.backend.lsp.session.LspSession
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidCloseTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.DocumentSymbolParams
import org.eclipse.lsp4j.HoverParams
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.TextDocumentContentChangeEvent
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextDocumentItem
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.xtext.util.CancelIndicator
import org.eclipse.xtext.xbase.lib.Functions
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.CompletableFuture

class SessionTextDocumentServiceTest {

    private val unknownUri = "file:///workspace/never-opened.oxsts"

    private class Fixture {
        val documents = mock<SessionDocumentManager>()
        val requestManager = mock<SessionRequestManager>()
        val lspSession = mock<LspSession> {
            on { this.requestManager } doReturn requestManager
            on { client() } doReturn mock<LanguageClient>()
        }
        val service = SessionTextDocumentService(lspSession, documents, mock<LanguageServices>())

        fun runReadsInline() {
            whenever(requestManager.runRead<Any?>(any())) doAnswer { invocation ->
                @Suppress("UNCHECKED_CAST")
                val block = invocation.arguments[0] as Functions.Function1<CancelIndicator, Any?>
                CompletableFuture.completedFuture(block.apply(CancelIndicator.NullImpl))
            }
        }

        fun runWritesInline() {
            whenever(requestManager.runWrite<Any?, Any?>(any(), any())) doAnswer { invocation ->
                @Suppress("UNCHECKED_CAST")
                val nonCancellable = invocation.arguments[0] as Functions.Function0<Any?>

                @Suppress("UNCHECKED_CAST")
                val cancellable = invocation.arguments[1] as Functions.Function2<CancelIndicator, Any?, Any?>
                CompletableFuture.completedFuture(cancellable.apply(CancelIndicator.NullImpl, nonCancellable.apply()))
            }
        }
    }

    private fun hoverParams(uri: String): HoverParams {
        return HoverParams(TextDocumentIdentifier(uri), Position(0, 0))
    }

    private fun documentSymbolParams(uri: String): DocumentSymbolParams {
        return DocumentSymbolParams(TextDocumentIdentifier(uri))
    }

    @Test
    fun `didChange for an unknown document is a no-op`() {
        val fixture = Fixture()
        fixture.service.didChange(
            DidChangeTextDocumentParams(
                VersionedTextDocumentIdentifier(unknownUri, 2),
                listOf(TextDocumentContentChangeEvent("anything")),
            ),
        )
        verify(fixture.documents).findByClient(unknownUri)
        verify(fixture.requestManager, never()).runWrite<Any?, Any?>(any(), any())
    }

    @Test
    fun `didClose delegates to closeByClient`() {
        val fixture = Fixture()
        fixture.service.didClose(DidCloseTextDocumentParams(TextDocumentIdentifier(unknownUri)))
        verify(fixture.documents).closeByClient(unknownUri)
    }

    @Test
    fun `didOpen for a fresh document opens it with the client text and validates`() {
        val fixture = Fixture().apply { runWritesInline() }
        whenever(fixture.documents.existsOnDisk(unknownUri)).thenReturn(false)
        whenever(fixture.documents.openByClient(unknownUri, "package x")).thenReturn(mock<SessionDocument>())

        fixture.service.didOpen(
            DidOpenTextDocumentParams(
                TextDocumentItem(unknownUri, "oxsts", 1, "package x"),
            ),
        )

        verify(fixture.documents).openByClient(unknownUri, "package x")
        verify(fixture.documents).validateAll(any(), any())
    }

    @Test
    fun `didOpen swallows a workspace-escaping URI without validating`() {
        val fixture = Fixture().apply { runWritesInline() }
        whenever(fixture.documents.existsOnDisk("file:///etc/passwd")).thenReturn(false)
        whenever(fixture.documents.openByClient(any(), any())).thenThrow(WorkspaceUriException("escapes workspace"))

        fixture.service.didOpen(
            DidOpenTextDocumentParams(TextDocumentItem("file:///etc/passwd", "oxsts", 1, "evil")),
        )

        verify(fixture.documents, never()).validateAll(any(), any())
    }

    @Test
    fun `hover on an unopened document returns null`() {
        val fixture = Fixture().apply { runReadsInline() }
        assertThat(fixture.service.hover(hoverParams(unknownUri)).get()).isNull()
    }

    @Test
    fun `documentSymbol on an unopened document returns an empty list`() {
        val fixture = Fixture().apply { runReadsInline() }
        assertThat(fixture.service.documentSymbol(documentSymbolParams(unknownUri)).get()).isEmpty()
    }
}
