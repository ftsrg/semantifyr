/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.artifact

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class ArtifactConfigTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `default enables every artifact kind except CompilationStep`() {
        val config = ArtifactConfig(outputDirectory = tempDir)

        for (kind in ArtifactKind.entries) {
            val expected = kind != ArtifactKind.CompilationStep
            assertThat(config.isEnabled(kind))
                .`as`("isEnabled($kind)")
                .isEqualTo(expected)
        }
        assertThat(config.enabledCompilationSteps).isEqualTo(CompilationStepsConfig.Off)
    }

    @Test
    fun `none() disables every artifact kind`() {
        val config = ArtifactConfig.none(tempDir)

        for (kind in ArtifactKind.entries) {
            assertThat(config.isEnabled(kind)).isFalse
        }
        assertThat(config.enabledCompilationSteps).isEqualTo(CompilationStepsConfig.Off)
    }

    @Test
    fun `all() enables every artifact kind except CompilationStep`() {
        val config = ArtifactConfig.all(tempDir)

        for (kind in ArtifactKind.entries) {
            val expected = kind != ArtifactKind.CompilationStep
            assertThat(config.isEnabled(kind))
                .`as`("isEnabled($kind)")
                .isEqualTo(expected)
        }
        assertThat(config.enabledCompilationSteps).isEqualTo(CompilationStepsConfig.Off)
    }

    @Test
    fun `debug() enables every artifact kind and turns compilation steps on`() {
        val config = ArtifactConfig.debug(tempDir)

        for (kind in ArtifactKind.entries) {
            assertThat(config.isEnabled(kind)).isTrue
        }
        assertThat(config.enabledCompilationSteps).isEqualTo(CompilationStepsConfig.All)
    }

    @Test
    fun `outputDirectory is preserved`() {
        val config = ArtifactConfig(outputDirectory = tempDir)
        assertThat(config.outputDirectory).isEqualTo(tempDir)
    }
}
