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
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.analyses.ConeOfInfluenceAnalysis
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.analyses.ConstantValueAnalysis
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.analyses.LivenessAnalysis
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.analyses.ReachingDefinitionsAnalysis
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.passes.ConstantAssumptionPropagationPass
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.passes.ConstantVariableSubstitutionPass
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.passes.CopyPropagationPass
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.passes.DeadCodeRemovalPass
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.passes.DeadStoreEliminationPass
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.passes.ExpressionSimplificationPass
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.passes.OperationFlatteningPass
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.passes.RedundantOperationRemovalPass
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.passes.VariableLivenessPass

/**
 * Optimizer applied after flattening, when each underlying variable has a
 * single canonical [hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration].
 *
 * Runs the full pass set, including every analysis-driven and
 * variable-manipulating pass. Running these here (instead of also in
 * [InlinedPhaseOptimizer]) is what keeps the analyses' grouping by variable
 * identity sound: after flattening, two expressions that refer to the same
 * underlying variable always resolve to the same declaration, so writes and
 * reads are grouped correctly.
 */
class FlattenedPhaseOptimizer @Inject constructor(
    livenessAnalysis: LivenessAnalysis,
    coneOfInfluenceAnalysis: ConeOfInfluenceAnalysis,
    constantValueAnalysis: ConstantValueAnalysis,
    reachingDefinitionsAnalysis: ReachingDefinitionsAnalysis,
    expressionSimplification: ExpressionSimplificationPass,
    operationFlattening: OperationFlatteningPass,
    redundantOperationRemoval: RedundantOperationRemovalPass,
    constantAssumptionPropagation: ConstantAssumptionPropagationPass,
    constantVariableSubstitution: ConstantVariableSubstitutionPass,
    copyPropagation: CopyPropagationPass,
    deadStoreElimination: DeadStoreEliminationPass,
    deadCodeRemoval: DeadCodeRemovalPass,
    variableLiveness: VariableLivenessPass,
) : Optimizer<InstantiatedCompilationContext>() {

    private val analysisManager = AnalysisManager(
        listOf(
            livenessAnalysis,
            coneOfInfluenceAnalysis,
            constantValueAnalysis,
            reachingDefinitionsAnalysis,
        )
    )

    private val pipeline = PassOptimizer(
        passes = listOf(
            // Cheap simplifications first.
            expressionSimplification,
            operationFlattening,
            redundantOperationRemoval,
            constantAssumptionPropagation,
            // Analysis-driven value propagation.
            constantVariableSubstitution,
            copyPropagation,
            // Analysis-driven dead code / store elimination.
            deadStoreElimination,
            deadCodeRemoval,
            // Cleanup after analyses remove things.
            variableLiveness,
        ),
        analysisManager = analysisManager,
    )

    override fun optimize(input: InstantiatedCompilationContext): Boolean {
        return pipeline.optimize(input)
    }

}
