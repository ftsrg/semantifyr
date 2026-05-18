/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.optimization

import hu.bme.mit.semantifyr.compiler.pipeline.artifact.CompilationArtifactManager
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.CompilationPass
import hu.bme.mit.semantifyr.compiler.pipeline.context.EvaluableCompilationContext

abstract class PatternOptimizationPass(
    private val config: OptimizationConfig,
    private val pass: OptimizationPass,
    compilationPass: CompilationPass,
    patterns: List<OptimizationPattern>,
    artifactManager: CompilationArtifactManager,
) : Pass<EvaluableCompilationContext> {

    private val patternOptimizer = PatternOptimizer(
        patterns = patterns,
        pass = compilationPass,
        artifactManager = artifactManager,
    )

    override fun run(input: EvaluableCompilationContext, analysisManager: AnalysisManager): PassResult {
        if (!config.isEnabled(pass)) {
            return PassResult.Unchanged
        }
        val changed = patternOptimizer.optimize(input.inlinedOxsts)
        return if (changed) {
            PassResult.Changed
        } else {
            PassResult.Unchanged
        }
    }

}
