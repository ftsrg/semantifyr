/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.artifact

import com.google.inject.Inject
import hu.bme.mit.semantifyr.compiler.pipeline.CompilationRequest
import hu.bme.mit.semantifyr.oxsts.lang.tests.InjectWithOxsts
import hu.bme.mit.semantifyr.oxsts.lang.tests.utils.InlinedOxstsParseHelper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

@InjectWithOxsts
class ArtifactManagerTest {
    @TempDir
    lateinit var tempDir: Path

    @Inject
    lateinit var parseHelper: InlinedOxstsParseHelper

    private val emptyInlined by lazy {
        parseHelper.parse(
            """
                inlined oxsts of semantifyr::Anything
                init {}
                tran {}
                prop { AG true }
            """.trimIndent(),
        )
    }

    private fun managerFor(config: ArtifactConfig): ArtifactManager {
        val request = CompilationRequest(inlinedOxsts = emptyInlined, outputDirectory = tempDir)
        return ArtifactManager(config, request)
    }

    @Test
    fun `pathOf covers every ArtifactKind`() {
        val manager = managerFor(ArtifactConfig.DEBUG)

        for (kind in ArtifactKind.entries) {
            val path = manager.pathOf(kind)
            assertThat(path).isNotBlank
        }
    }

    @Test
    fun `pathOf returns distinct paths for distinct kinds`() {
        val manager = managerFor(ArtifactConfig.DEBUG)

        val paths = ArtifactKind.entries.map {
            manager.pathOf(it)
        }
        assertThat(paths).doesNotHaveDuplicates()
    }

    @Test
    fun `resolveUri resolves a relative path against the output directory`() {
        val manager = managerFor(ArtifactConfig.DEBUG)

        val uri = manager.resolveUri("sub/model.oxsts")

        assertThat(uri.toFileString()).isEqualTo(tempDir.resolve("sub/model.oxsts").toFile().absolutePath)
    }

    @Test
    fun `resolveUri by ArtifactKind uses the kind-specific path`() {
        val manager = managerFor(ArtifactConfig.DEBUG)
        val expected = tempDir.resolve(manager.pathOf(ArtifactKind.Witness)).toFile().absolutePath

        val uri = manager.resolveUri(ArtifactKind.Witness)

        assertThat(uri.toFileString()).isEqualTo(expected)
    }

    @Test
    fun `withFile does not invoke block when the artifact kind is disabled`() {
        val manager = managerFor(ArtifactConfig.NONE)
        var invoked = false

        manager.withFile(ArtifactKind.Witness) {
            invoked = true
        }

        assertThat(invoked).isFalse
    }

    @Test
    fun `withFile invokes block with the resolved file when the kind is enabled`() {
        val config = ArtifactConfig(enabled = setOf(ArtifactKind.Witness))
        val manager = managerFor(config)

        lateinit var receivedPath: Path
        manager.withFile(ArtifactKind.Witness) {
            receivedPath = it.toPath()
            it.writeText("hello")
        }

        assertThat(receivedPath).isEqualTo(tempDir.resolve(manager.pathOf(ArtifactKind.Witness)))
        assertThat(Files.readString(receivedPath)).isEqualTo("hello")
    }

    @Test
    fun `withFile creates parent directories on demand`() {
        val config = ArtifactConfig(enabled = setOf(ArtifactKind.InstantiatedModel))
        val manager = managerFor(config)

        manager.withFile(ArtifactKind.InstantiatedModel) {
            it.writeText("x")
        }

        val expected = tempDir.resolve(manager.pathOf(ArtifactKind.InstantiatedModel))
        assertThat(Files.exists(expected.parent)).isTrue
        assertThat(Files.readString(expected)).isEqualTo("x")
    }
}
