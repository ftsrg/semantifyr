/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.optimization.passes

import com.google.inject.Inject
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.CompilationArtifactManager
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.CompilationPass
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.PatternOptimizer
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.patterns.expression.NegationNormalizationPatterns
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts

class PropertyExpressionNormalizationPass @Inject constructor(
    artifactManager: CompilationArtifactManager,
) {

    private val optimizer = PatternOptimizer(
        patterns = listOf(NegationNormalizationPatterns()),
        pass = CompilationPass.PropertyExpressionNormalization,
        artifactManager = artifactManager,
    )

    fun normalize(inlinedOxsts: InlinedOxsts): Boolean {
        return optimizer.optimize(inlinedOxsts.property.expression)
    }

}
