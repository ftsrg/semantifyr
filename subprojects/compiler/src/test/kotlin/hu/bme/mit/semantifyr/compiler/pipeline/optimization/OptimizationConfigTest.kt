/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.optimization

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class OptimizationConfigTest {
    @Test
    fun `NONE enables nothing`() {
        for (pass in OptimizationPass.entries) {
            assertThat(OptimizationConfig.NONE.isEnabled(pass)).isFalse
        }
    }

    @Test
    fun `ALL enables every pass`() {
        for (pass in OptimizationPass.entries) {
            assertThat(OptimizationConfig.ALL.isEnabled(pass)).isTrue
        }
    }

    @Test
    fun `custom config enables only the specified passes`() {
        val config = OptimizationConfig(enabled = setOf(OptimizationPass.ExpressionSimplification))

        assertThat(config.isEnabled(OptimizationPass.ExpressionSimplification)).isTrue
        assertThat(config.isEnabled(OptimizationPass.OperationFlattening)).isFalse
        assertThat(config.isEnabled(OptimizationPass.DeadCodeRemoval)).isFalse
    }

    @Test
    fun `default constructor enables every pass`() {
        val config = OptimizationConfig()

        for (pass in OptimizationPass.entries) {
            assertThat(config.isEnabled(pass)).isTrue
        }
    }
}
