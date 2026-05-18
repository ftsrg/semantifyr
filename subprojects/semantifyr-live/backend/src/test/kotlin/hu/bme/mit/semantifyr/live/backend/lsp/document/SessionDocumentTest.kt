/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.lsp.document

import hu.bme.mit.semantifyr.live.backend.lsp.language.LanguageServices
import hu.bme.mit.semantifyr.live.backend.lsp.language.LiveOxstsLanguageSetup
import hu.bme.mit.semantifyr.live.backend.testing.emptyGlobalsModule
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.TextDocumentContentChangeEvent
import org.eclipse.xtext.resource.XtextResource
import org.junit.jupiter.api.Test
import java.nio.file.Path
import org.eclipse.emf.common.util.URI as EmfURI

class SessionDocumentTest {

    private fun document(text: String): SessionDocument {
        val serverUri = "file:///workspace/test.oxsts"
        val emfUri = EmfURI.createURI(serverUri)
        val languageServices = LiveOxstsLanguageSetup(emptyGlobalsModule())
            .createInjectorAndDoEMFRegistration()
            .getInstance(LanguageServices::class.java)
        val resource = languageServices.newResourceSet().createResource(emfUri) as XtextResource
        return SessionDocument(serverUri, emfUri, resource, Path.of("/tmp/test.oxsts"), text)
    }

    private fun fullChange(text: String): TextDocumentContentChangeEvent {
        return TextDocumentContentChangeEvent().apply {
            this.text = text
        }
    }

    private fun rangeChange(
        startLine: Int,
        startCharacter: Int,
        endLine: Int,
        endCharacter: Int,
        text: String,
    ) = TextDocumentContentChangeEvent().apply {
        this.range = Range(Position(startLine, startCharacter), Position(endLine, endCharacter))
        this.text = text
    }

    @Test
    fun `a full-document change replaces the text`() {
        val document = document("package x")
        document.applyChanges(listOf(fullChange("package y")))
        assertThat(document.text()).isEqualTo("package y")
    }

    @Test
    fun `an empty change list is a no-op`() {
        val document = document("package x")
        document.applyChanges(emptyList())
        assertThat(document.text()).isEqualTo("package x")
    }

    @Test
    fun `a single-line range edit replaces only that span`() {
        val document = document("package abc")
        document.applyChanges(listOf(rangeChange(0, 8, 0, 11, "xyz")))
        assertThat(document.text()).isEqualTo("package xyz")
    }

    @Test
    fun `a multi-line range edit splices across the lines`() {
        val document = document("line0\nline1\nline2")
        document.applyChanges(listOf(rangeChange(1, 0, 2, 0, "")))
        assertThat(document.text()).isEqualTo("line0\nline2")
    }

    @Test
    fun `successive changes in one batch are applied left to right`() {
        val document = document("abcdef")
        document.applyChanges(
            listOf(
                rangeChange(0, 0, 0, 1, "X"),
                rangeChange(0, 5, 0, 6, "Y"),
            ),
        )
        assertThat(document.text()).isEqualTo("XbcdeY")
    }
}
