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
        for (category in OptimizationCategory.entries) {
            assertThat(OptimizationConfig.NONE.isEnabled(category)).isFalse
        }
    }

    @Test
    fun `ALL enables every category`() {
        for (category in OptimizationCategory.entries) {
            assertThat(OptimizationConfig.ALL.isEnabled(category)).isTrue
        }
    }

    @Test
    fun `DEFAULT is equivalent to ALL`() {
        assertThat(OptimizationConfig.DEFAULT).isEqualTo(OptimizationConfig.ALL)
    }

    @Test
    fun `custom config enables only the specified categories`() {
        val config = OptimizationConfig(enabled = setOf(OptimizationCategory.ConstantFolding))

        assertThat(config.isEnabled(OptimizationCategory.ConstantFolding)).isTrue
        assertThat(config.isEnabled(OptimizationCategory.OperationFlattening)).isFalse
        assertThat(config.isEnabled(OptimizationCategory.DeadCodeElimination)).isFalse
    }

    @Test
    fun `isAnyEnabled returns true when at least one given category is enabled`() {
        val config = OptimizationConfig(enabled = setOf(OptimizationCategory.OperationFlattening))

        assertThat(
            config.isAnyEnabled(
                OptimizationCategory.ConstantFolding,
                OptimizationCategory.OperationFlattening,
            ),
        ).isTrue
    }

    @Test
    fun `isAnyEnabled returns false when none of the given categories are enabled`() {
        val config = OptimizationConfig(enabled = setOf(OptimizationCategory.OperationFlattening))

        assertThat(
            config.isAnyEnabled(
                OptimizationCategory.ConstantFolding,
                OptimizationCategory.AssumeFalsePropagation,
            ),
        ).isFalse
    }

    @Test
    fun `isAnyEnabled with no arguments is false`() {
        assertThat(OptimizationConfig.ALL.isAnyEnabled()).isFalse
    }

    @Test
    fun `default constructor enables every category`() {
        val config = OptimizationConfig()

        for (category in OptimizationCategory.entries) {
            assertThat(config.isEnabled(category)).isTrue
        }
    }
}
