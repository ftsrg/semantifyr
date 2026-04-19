/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.optimization.passes

import com.google.inject.Inject
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.CompilationArtifactManager
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.CompilationPass
import hu.bme.mit.semantifyr.compiler.pipeline.context.InstantiatedCompilationContext
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.AnalysisManager
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationCategory
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationConfig
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.optimizers.Pass
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.optimizers.PassResult
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.WorklistOptimizer
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.patterns.PropagateBothBranchesConstantFalsePattern
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.patterns.PropagateConstantFalseInSequencePattern
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.patterns.PropagateSingleBranchConstantFalsePattern
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.patterns.RemoveConstantFalseChoiceBranchPattern

class ConstantAssumptionPropagationPass @Inject constructor(
    private val config: OptimizationConfig,
    artifactManager: CompilationArtifactManager,
) : Pass<InstantiatedCompilationContext> {

    private val worklistOptimizer = WorklistOptimizer(
        patterns = listOf(
            PropagateConstantFalseInSequencePattern(),
            RemoveConstantFalseChoiceBranchPattern(),
            PropagateSingleBranchConstantFalsePattern(),
            PropagateBothBranchesConstantFalsePattern(),
        ),
        pass = CompilationPass.AssumptionPropagation,
        artifactManager = artifactManager,
    )

    override fun run(input: InstantiatedCompilationContext, analyses: AnalysisManager): PassResult {
        if (!config.isEnabled(OptimizationCategory.AssumptionPropagation)) {
            return PassResult.Unchanged
        }
        val changed = worklistOptimizer.optimize(input.inlinedOxsts)
        return if (changed) PassResult.changed() else PassResult.Unchanged
    }

}
