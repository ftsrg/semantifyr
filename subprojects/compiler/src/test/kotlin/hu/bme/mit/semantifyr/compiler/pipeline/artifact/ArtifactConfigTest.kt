/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.artifact

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ArtifactConfigTest {
    @Test
    fun `default enables every artifact kind except CompilationStep`() {
        val config = ArtifactConfig()

        for (kind in ArtifactKind.entries) {
            val expected = kind != ArtifactKind.CompilationStep
            assertThat(config.isEnabled(kind))
                .`as`("isEnabled($kind)")
                .isEqualTo(expected)
        }
        assertThat(config.enabledCompilationSteps).isEqualTo(CompilationStepsConfig.Off)
    }

    @Test
    fun `NONE disables every artifact kind`() {
        val config = ArtifactConfig.NONE

        for (kind in ArtifactKind.entries) {
            assertThat(config.isEnabled(kind)).isFalse
        }
        assertThat(config.enabledCompilationSteps).isEqualTo(CompilationStepsConfig.Off)
    }

    @Test
    fun `ALL enables every artifact kind except CompilationStep`() {
        val config = ArtifactConfig.ALL

        for (kind in ArtifactKind.entries) {
            val expected = kind != ArtifactKind.CompilationStep
            assertThat(config.isEnabled(kind))
                .`as`("isEnabled($kind)")
                .isEqualTo(expected)
        }
        assertThat(config.enabledCompilationSteps).isEqualTo(CompilationStepsConfig.Off)
    }

    @Test
    fun `DEBUG enables every artifact kind and turns compilation steps on`() {
        val config = ArtifactConfig.DEBUG

        for (kind in ArtifactKind.entries) {
            assertThat(config.isEnabled(kind)).isTrue
        }
        assertThat(config.enabledCompilationSteps).isEqualTo(CompilationStepsConfig.All)
    }
}
