/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.lsp.document

import hu.bme.mit.semantifyr.live.backend.exceptions.WorkspaceUriException
import hu.bme.mit.semantifyr.live.backend.lsp.language.LanguageServices
import hu.bme.mit.semantifyr.live.backend.lsp.language.LiveOxstsLanguageSetup
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class SessionDocumentManagerTest {

    private fun buildManager(workingDirectory: Path): SessionDocumentManager {
        val languageServices = LiveOxstsLanguageSetup()
            .createInjectorAndDoEMFRegistration()
            .getInstance(LanguageServices::class.java)
        return SessionDocumentManager(languageServices, workingDirectory)
    }

    @Test
    fun `openByClient writes nothing to disk on open`(@TempDir tmp: Path) {
        val manager = buildManager(tmp)
        manager.openByClient("file:///workspace/snippet.oxsts", "package x")
        val onDisk = tmp.resolve("snippet.oxsts")
        assertThat(Files.exists(onDisk)).isFalse()
    }

    @Test
    fun `flushAllToDisk writes the in-memory text to disk`(@TempDir tmp: Path) {
        val manager = buildManager(tmp)
        manager.openByClient("file:///workspace/snippet.oxsts", "package x")
        manager.flushAllToDisk()
        val onDisk = tmp.resolve("snippet.oxsts")
        assertThat(Files.readString(onDisk)).isEqualTo("package x")
    }

    @Test
    fun `openByClient stores by server URI, findByClient and findByServer agree`(@TempDir tmp: Path) {
        val manager = buildManager(tmp)
        val opened = manager.openByClient("file:///workspace/a.oxsts", "package x")
        val serverUri = tmp.resolve("a.oxsts").toUri().toString()
        assertThat(manager.findByClient("file:///workspace/a.oxsts")).isSameAs(opened)
        assertThat(manager.findByServer(serverUri)).isSameAs(opened)
    }

    @Test
    fun `find accepts either client or server URI form`(@TempDir tmp: Path) {
        val manager = buildManager(tmp)
        val opened = manager.openByClient("file:///workspace/b.oxsts", "package x")
        val serverUri = tmp.resolve("b.oxsts").toUri().toString()
        assertThat(manager.find("file:///workspace/b.oxsts")).isSameAs(opened)
        assertThat(manager.find(serverUri)).isSameAs(opened)
    }

    @Test
    fun `closeByClient removes the document and returns true`(@TempDir tmp: Path) {
        val manager = buildManager(tmp)
        manager.openByClient("file:///workspace/c.oxsts", "package x")
        assertThat(manager.closeByClient("file:///workspace/c.oxsts")).isTrue()
        assertThat(manager.findByClient("file:///workspace/c.oxsts")).isNull()
    }

    @Test
    fun `closeByClient returns false for an unknown URI`(@TempDir tmp: Path) {
        val manager = buildManager(tmp)
        assertThat(manager.closeByClient("file:///workspace/missing.oxsts")).isFalse()
    }

    @Test
    fun `openByServer rejects URIs outside the workspace`(@TempDir tmp: Path) {
        val manager = buildManager(tmp)
        assertThatThrownBy { manager.openByServer("file:///etc/passwd", "evil") }
            .isInstanceOf(WorkspaceUriException::class.java)
            .hasMessageContaining("escapes workspace")
    }

    @Test
    fun `openByServer rejects non-file URIs`(@TempDir tmp: Path) {
        val manager = buildManager(tmp)
        assertThatThrownBy { manager.openByServer("http://example.com/a.oxsts", "x") }
            .isInstanceOf(WorkspaceUriException::class.java)
            .hasMessageContaining("non-file uri")
    }

    @Test
    fun `serverToClient rewrites the workspace prefix`(@TempDir tmp: Path) {
        val manager = buildManager(tmp)
        val serverUri = tmp.resolve("snippet.oxsts").toUri().toString()
        assertThat(manager.serverToClient(serverUri)).isEqualTo("file:///workspace/snippet.oxsts")
    }

    @Test
    fun `openExistingByServer reads the file from disk and does not change it`(@TempDir tmp: Path) {
        val manager = buildManager(tmp)
        val target = tmp.resolve("witness.oxsts")
        Files.writeString(target, "package preexisting")
        val serverUri = target.toUri().toString()

        val document = manager.openExistingByServer(serverUri)
        assertThat(document.uri).isEqualTo(serverUri)
        assertThat(Files.readString(target)).isEqualTo("package preexisting")

        manager.flushAllToDisk()
        assertThat(Files.readString(target)).isEqualTo("package preexisting")
    }

    @Test
    fun `openExistingByServer fails if the file does not exist`(@TempDir tmp: Path) {
        val manager = buildManager(tmp)
        val serverUri = tmp.resolve("ghost.oxsts").toUri().toString()
        assertThatThrownBy { manager.openExistingByServer(serverUri) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("does not exist")
    }

    @Test
    fun `toClientUri rewrites a workspace path uri to the workspace prefix`(@TempDir tmp: Path) {
        val manager = buildManager(tmp)
        val witnessPath = tmp.resolve(".artifacts").resolve("Case").resolve("witness.oxsts")
        assertThat(manager.toClientUri(witnessPath.toUri().toString()))
            .isEqualTo("file:///workspace/.artifacts/Case/witness.oxsts")
    }

    @Test
    fun `toClientUri accepts the EMF single-slash file uri form`(@TempDir tmp: Path) {
        val manager = buildManager(tmp)
        val witnessPath = tmp.resolve(".artifacts").resolve("Case").resolve("witness.oxsts")
        val emfStyleUri = "file:${witnessPath.toAbsolutePath().normalize()}"
        assertThat(manager.toClientUri(emfStyleUri))
            .isEqualTo("file:///workspace/.artifacts/Case/witness.oxsts")
    }

    @Test
    fun `toClientUri leaves uris outside the workspace untouched`(@TempDir tmp: Path) {
        val manager = buildManager(tmp)
        val outside = "file:///tmp/semantifyr-1234/Case/witness.oxsts"
        assertThat(manager.toClientUri(outside)).isEqualTo(outside)
    }

    @Test
    fun `readDocumentText returns the in-memory text for an open document`(@TempDir tmp: Path) {
        val manager = buildManager(tmp)
        manager.openByClient("file:///workspace/snippet.oxsts", "package x")
        assertThat(manager.readDocumentText("file:///workspace/snippet.oxsts")).isEqualTo("package x")
    }

    @Test
    fun `readDocumentText falls back to the on-disk content for a closed file`(@TempDir tmp: Path) {
        val manager = buildManager(tmp)
        val target = tmp.resolve(".artifacts").resolve("Case").resolve("witness.oxsts")
        Files.createDirectories(target.parent)
        Files.writeString(target, "package preexisting")
        assertThat(manager.readDocumentText("file:///workspace/.artifacts/Case/witness.oxsts"))
            .isEqualTo("package preexisting")
    }

    @Test
    fun `readDocumentText returns null for a missing file`(@TempDir tmp: Path) {
        val manager = buildManager(tmp)
        assertThat(manager.readDocumentText("file:///workspace/ghost.oxsts")).isNull()
    }
}
