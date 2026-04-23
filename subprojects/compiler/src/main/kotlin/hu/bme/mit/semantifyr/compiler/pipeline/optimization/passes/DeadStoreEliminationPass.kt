/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.optimization.passes

import com.google.inject.Inject
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AssignmentOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.HavocOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Operation
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.CompilationArtifactManager
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.CompilationPass
import hu.bme.mit.semantifyr.compiler.pipeline.context.EvaluableCompilationContext
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.AnalysisManager
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationCategory
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationConfig
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.Pass
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.PassResult
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.analyses.ReachingDefinitionsAnalysis
import hu.bme.mit.semantifyr.compiler.pipeline.utils.eAllOfType
import org.eclipse.xtext.EcoreUtil2

/**
 * Removes assignments and havocs whose written value is never observed.
 *
 * Classical example: `x := 10; ...; x := 20` where `x` is not read between
 * the two writes. The first `x := 10` is dead because only the second write
 * reaches any subsequent read of `x`.
 *
 * Uses [ReachingDefinitionsAnalysis] to find the set of "live" writes
 * that appear in at least one read's reaching-definition set.
 * Any write not in that set can be removed.
 */
class DeadStoreEliminationPass @Inject constructor(
    private val config: OptimizationConfig,
    private val artifactManager: CompilationArtifactManager,
) : Pass<EvaluableCompilationContext> {

    override fun run(input: EvaluableCompilationContext, analysisManager: AnalysisManager): PassResult {
        if (!config.isEnabled(OptimizationCategory.RedundantOperationRemoval)) {
            return PassResult.Unchanged
        }

        val rd = analysisManager.get(ReachingDefinitionsAnalysis::class.java, input)

        // "Live" writes: those that are the reaching definition for some read.
        // The set may also contain VariableDeclarations (initializers); we keep
        // them as sentinels here without narrowing since the dead-write filter
        // below only looks at assignment/havoc operations anyway.
        val liveWrites = rd.defsOf.values.flatten().toSet()

        val deadWrites = input.inlinedOxsts.eAllOfType<Operation>()
            .filter { it is AssignmentOperation || it is HavocOperation }
            .filter { it !in liveWrites }
            .toList()

        if (deadWrites.isEmpty()) {
            return PassResult.Unchanged
        }

        for (operation in deadWrites) {
            EcoreUtil2.remove(operation)
            artifactManager.commitStep(CompilationPass.DeadStoreElimination)
        }
        return PassResult.Changed()
    }

}
