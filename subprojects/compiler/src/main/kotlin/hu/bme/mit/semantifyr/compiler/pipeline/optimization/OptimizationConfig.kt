/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.optimization

import kotlinx.serialization.Serializable

@Serializable
enum class OptimizationPass {
    ExpressionSimplification,
    OperationFlattening,
    RedundantOperationRemoval,
    AssumeFalsePropagation,
    ConstantVariableSubstitution,
    CopyPropagation,
    DeadStoreElimination,
    DeadCodeRemoval,
    VariableLiveness,
}

@Serializable
data class OptimizationConfig(
    val enabled: Set<OptimizationPass> = OptimizationPass.entries.toSet(),
) {
    fun isEnabled(pass: OptimizationPass): Boolean {
        return pass in enabled
    }

    companion object {
        val NONE = OptimizationConfig(enabled = emptySet())
        val ALL = OptimizationConfig(enabled = OptimizationPass.entries.toSet())
    }
}
