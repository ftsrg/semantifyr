/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.semantics.optimization

enum class OptimizationCategory {
    ConstantFolding,
    ExpressionSimplification,
    RedundantOperationRemoval,
    OperationFlattening,
    AssumptionPropagation,
    UnusedVariableElimination,
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
        @JvmField
        val NONE = OptimizationConfig(enabled = emptySet())

        @JvmField
        val DEFAULT = OptimizationConfig()

        @JvmField
        val ALL = OptimizationConfig()
    }
}
