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
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.Pass
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.PassResult
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.WorklistOptimizer
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.patterns.FlattenNestedChoicePattern
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.patterns.FlattenNestedSequencePattern
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.patterns.FlattenSingleBranchChoicePattern

class OperationFlatteningPass @Inject constructor(
    private val config: OptimizationConfig,
    artifactManager: CompilationArtifactManager,
) : Pass<InstantiatedCompilationContext> {

    private val worklistOptimizer = WorklistOptimizer(
        patterns = listOf(
            FlattenNestedSequencePattern(),
            FlattenNestedChoicePattern(),
            FlattenSingleBranchChoicePattern(),
        ),
        pass = CompilationPass.OperationFlattening,
        artifactManager = artifactManager,
    )

    override fun run(input: InstantiatedCompilationContext, analyses: AnalysisManager): PassResult {
        if (!config.isEnabled(OptimizationCategory.OperationFlattening)) {
            return PassResult.Unchanged
        }
        val changed = worklistOptimizer.optimize(input.inlinedOxsts)
        return if (changed) PassResult.changed() else PassResult.Unchanged
    }

}
