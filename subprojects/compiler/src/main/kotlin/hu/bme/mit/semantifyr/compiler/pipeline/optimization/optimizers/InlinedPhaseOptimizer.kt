/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.optimization.optimizers

import com.google.inject.Inject
import hu.bme.mit.semantifyr.compiler.pipeline.context.InstantiatedCompilationContext
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.AnalysisManager
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.Optimizer
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.passes.ConstantAssumptionPropagationPass
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.passes.ExpressionSimplificationPass
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.passes.OperationFlatteningPass
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.passes.RedundantOperationRemovalPass

/**
 * Optimizer applied during the inlining phase, before flattening.
 *
 * Runs only pattern-based passes that rewrite operations and expressions
 * without reasoning about variable identity. In particular, the analysis-driven
 * passes ([hu.bme.mit.semantifyr.compiler.pipeline.optimization.passes.CopyPropagationPass],
 * [hu.bme.mit.semantifyr.compiler.pipeline.optimization.passes.DeadStoreEliminationPass],
 * [hu.bme.mit.semantifyr.compiler.pipeline.optimization.passes.DeadCodeRemovalPass],
 * [hu.bme.mit.semantifyr.compiler.pipeline.optimization.passes.ConstantVariableSubstitutionPass],
 * [hu.bme.mit.semantifyr.compiler.pipeline.optimization.passes.VariableLivenessPass]) are NOT
 * run here - they group writes and reads by the [hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration]
 * the evaluator resolves a reference to. Before flattening, gamma-style IR
 * accesses one underlying variable via multiple navigation paths (e.g.
 * `state1.parentRegion.activeState` and `state2.parentRegion.activeState`),
 * and those paths may not canonicalize to the same declaration. Running a
 * variable-identity-based pass in that state wrongly groups the writes and
 * erases property-relevant ones, producing a model where the target state is
 * unreachable.
 *
 * All variable-manipulating passes are deferred to [FlattenedPhaseOptimizer],
 * which runs after flattening has merged aliased paths onto a single flat
 * [hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration] per variable.
 */
class InlinedPhaseOptimizer @Inject constructor(
    expressionSimplification: ExpressionSimplificationPass,
    operationFlattening: OperationFlatteningPass,
    redundantOperationRemoval: RedundantOperationRemovalPass,
    constantAssumptionPropagation: ConstantAssumptionPropagationPass,
) : Optimizer<InstantiatedCompilationContext>() {

    private val pipeline = PassOptimizer(
        passes = listOf(
            expressionSimplification,
            operationFlattening,
            redundantOperationRemoval,
            constantAssumptionPropagation,
        ),
        analysisManager = AnalysisManager(emptyList()),
    )

    override fun optimize(input: InstantiatedCompilationContext): Boolean {
        return pipeline.optimize(input)
    }

}
