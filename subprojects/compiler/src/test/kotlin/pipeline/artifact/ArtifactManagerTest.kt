/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.artifact

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class ArtifactManagerTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `pathOf covers every ArtifactKind`() {
        val manager = ArtifactManager(ArtifactConfig.debug(tempDir))

        for (kind in ArtifactKind.entries) {
            // Should not throw and must return a non-empty path string.
            val path = manager.pathOf(kind)
            assertThat(path).isNotBlank
        }
    }

    @Test
    fun `pathOf returns distinct paths for distinct kinds`() {
        val manager = ArtifactManager(ArtifactConfig.debug(tempDir))

        val paths = ArtifactKind.entries.map {
            manager.pathOf(it)
        }
        assertThat(paths).doesNotHaveDuplicates()
    }

    @Test
    fun `resolveUri resolves a relative path against the output directory`() {
        val manager = ArtifactManager(ArtifactConfig.debug(tempDir))

        val uri = manager.resolveUri("sub/model.oxsts")

        assertThat(uri.toFileString()).isEqualTo(tempDir.resolve("sub/model.oxsts").toFile().absolutePath)
    }

    @Test
    fun `resolveUri by ArtifactKind uses the kind-specific path`() {
        val manager = ArtifactManager(ArtifactConfig.debug(tempDir))
        val expected = tempDir.resolve(manager.pathOf(ArtifactKind.Witness)).toFile().absolutePath

        val uri = manager.resolveUri(ArtifactKind.Witness)

        assertThat(uri.toFileString()).isEqualTo(expected)
    }

    @Test
    fun `withFile does not invoke block when the artifact kind is disabled`() {
        val manager = ArtifactManager(ArtifactConfig.none(tempDir))
        var invoked = false

        manager.withFile(ArtifactKind.Witness) { invoked = true }

        assertThat(invoked).isFalse
    }

    @Test
    fun `withFile invokes block with the resolved file when the kind is enabled`() {
        val config = ArtifactConfig(outputDirectory = tempDir, enabled = setOf(ArtifactKind.Witness))
        val manager = ArtifactManager(config)

        var receivedPath: Path? = null
        manager.withFile(ArtifactKind.Witness) {
            receivedPath = it.toPath()
            it.writeText("hello")
        }

        assertThat(receivedPath).isEqualTo(tempDir.resolve(manager.pathOf(ArtifactKind.Witness)))
        assertThat(Files.readString(receivedPath!!)).isEqualTo("hello")
    }

    @Test
    fun `withFile creates parent directories on demand`() {
        val config = ArtifactConfig(outputDirectory = tempDir, enabled = setOf(ArtifactKind.InflatedModel))
        val manager = ArtifactManager(config)

        manager.withFile(ArtifactKind.InflatedModel) {
            it.writeText("x")
        }

        val expected = tempDir.resolve(manager.pathOf(ArtifactKind.InflatedModel))
        assertThat(Files.exists(expected.parent)).isTrue
        assertThat(Files.readString(expected)).isEqualTo("x")
    }
}
