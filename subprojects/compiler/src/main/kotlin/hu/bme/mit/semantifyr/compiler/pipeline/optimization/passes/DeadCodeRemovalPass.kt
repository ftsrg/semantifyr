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
import hu.bme.mit.semantifyr.compiler.pipeline.context.InstantiatedCompilationContext
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.AnalysisManager
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationCategory
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationConfig
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.Pass
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.PassResult
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.analyses.ConeOfInfluenceAnalysis
import hu.bme.mit.semantifyr.compiler.pipeline.utils.eAllOfType
import org.eclipse.xtext.EcoreUtil2

/**
 * Uses [ConeOfInfluenceAnalysis] to remove assignments and havoc operations
 * whose target variable is not in the cone of influence of the property.
 */
class DeadCodeRemovalPass @Inject constructor(
    private val config: OptimizationConfig,
    private val artifactManager: CompilationArtifactManager,
) : Pass<InstantiatedCompilationContext> {

    override fun run(input: InstantiatedCompilationContext, analyses: AnalysisManager): PassResult {
        if (!config.isEnabled(OptimizationCategory.DeadCodeElimination)) {
            return PassResult.Unchanged
        }

        val cone = analyses.get(ConeOfInfluenceAnalysis::class.java, input)

        val deadOperations = input.inlinedOxsts.eAllOfType<Operation>()
            .filter { it is AssignmentOperation || it is HavocOperation }
            .filterNot { cone.isRelevant(it) }
            .toList()

        if (deadOperations.isEmpty()) {
            return PassResult.Unchanged
        }

        for (operation in deadOperations) {
            EcoreUtil2.remove(operation)
            artifactManager.commitStep(CompilationPass.DeadCodeRemoval)
        }
        return PassResult.changed()
    }

}
