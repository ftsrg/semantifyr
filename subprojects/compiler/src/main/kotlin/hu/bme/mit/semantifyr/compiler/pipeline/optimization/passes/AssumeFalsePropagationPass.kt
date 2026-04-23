/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.optimization.passes

import com.google.inject.Inject
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.CompilationArtifactManager
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.CompilationPass
import hu.bme.mit.semantifyr.compiler.pipeline.context.EvaluableCompilationContext
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.AnalysisManager
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationCategory
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationConfig
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.Pass
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.PassResult
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.PatternOptimizer
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.patterns.AssumeFalsePropagationPattern

class AssumeFalsePropagationPass @Inject constructor(
    private val config: OptimizationConfig,
    artifactManager: CompilationArtifactManager,
) : Pass<EvaluableCompilationContext> {

    private val patternOptimizer = PatternOptimizer(
        patterns = listOf(
            AssumeFalsePropagationPattern(),
        ),
        pass = CompilationPass.AssumptionPropagation,
        artifactManager = artifactManager,
    )

    override fun run(input: EvaluableCompilationContext, analysisManager: AnalysisManager): PassResult {
        if (!config.isEnabled(OptimizationCategory.AssumeFalsePropagation)) {
            return PassResult.Unchanged
        }
        val changed = patternOptimizer.optimize(input.inlinedOxsts)
        return if (changed) {
            PassResult.changed()
        } else {
            PassResult.Unchanged
        }
    }

}
