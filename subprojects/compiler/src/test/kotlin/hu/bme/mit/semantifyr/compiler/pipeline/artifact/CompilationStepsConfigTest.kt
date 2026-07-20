/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.artifact

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CompilationStepsConfigTest {
    @Test
    fun `Off emits no passes`() {
        for (pass in CompilationPass.entries) {
            assertThat(CompilationStepsConfig.Off.shouldEmit(pass)).isFalse
        }
    }

    @Test
    fun `All emits every pass`() {
        for (pass in CompilationPass.entries) {
            assertThat(CompilationStepsConfig.All.shouldEmit(pass)).isTrue
        }
    }

    @Test
    fun `Selected emits only the listed passes`() {
        val config = CompilationStepsConfig.Selected(
            setOf(CompilationPass.ConstantFolding, CompilationPass.OperationFlattening),
        )

        assertThat(config.shouldEmit(CompilationPass.ConstantFolding)).isTrue
        assertThat(config.shouldEmit(CompilationPass.OperationFlattening)).isTrue
        assertThat(config.shouldEmit(CompilationPass.DeadCodeRemoval)).isFalse
        assertThat(config.shouldEmit(CompilationPass.Flattening)).isFalse
    }

    @Test
    fun `Selected with empty set emits nothing`() {
        val config = CompilationStepsConfig.Selected(emptySet())

        for (pass in CompilationPass.entries) {
            assertThat(config.shouldEmit(pass)).isFalse
        }
    }
}
