/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.optimization.optimizers

import com.google.inject.Inject
import hu.bme.mit.semantifyr.compiler.pipeline.context.EvaluableCompilationContext
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.AnalysisManager
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.Optimizer
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.PassOptimizer
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.passes.AssumeFalsePropagationPass
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.passes.ExpressionSimplificationPass
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.passes.OperationFlatteningPass
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.passes.RedundantOperationRemovalPass

class InlinedPhaseOptimizer @Inject constructor(
    expressionSimplification: ExpressionSimplificationPass,
    operationFlattening: OperationFlatteningPass,
    redundantOperationRemoval: RedundantOperationRemovalPass,
    constantAssumptionPropagation: AssumeFalsePropagationPass,
) : Optimizer<EvaluableCompilationContext>() {

    private val pipeline = PassOptimizer(
        passes = listOf(
            expressionSimplification,
            operationFlattening,
            redundantOperationRemoval,
            constantAssumptionPropagation,
        ),
        analysisManager = AnalysisManager(emptyList()),
    )

    override fun optimize(input: EvaluableCompilationContext): Boolean {
        return pipeline.optimize(input)
    }

}
