/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.optimization

enum class OptimizationCategory {
    ConstantFolding,
    ExpressionSimplification,
    RedundantOperationRemoval,
    OperationFlattening,
    AssumeFalsePropagation,
    UnusedVariableElimination,
    DeadCodeElimination,
}

data class OptimizationConfig(
    val enabled: Set<OptimizationCategory> = OptimizationCategory.entries.toSet(),
) {
    fun isEnabled(category: OptimizationCategory): Boolean {
        return category in enabled
    }

    fun isAnyEnabled(vararg categories: OptimizationCategory): Boolean {
        return categories.any(::isEnabled)
    }

    companion object {
        val NONE = OptimizationConfig(enabled = emptySet())
        val ALL = OptimizationConfig(enabled = OptimizationCategory.entries.toSet())
        val DEFAULT = ALL
    }
}
