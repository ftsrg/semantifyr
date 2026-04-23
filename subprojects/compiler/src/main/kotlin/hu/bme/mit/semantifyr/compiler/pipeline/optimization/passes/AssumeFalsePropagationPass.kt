/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.optimization.passes

import com.google.inject.Inject
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.CompilationArtifactManager
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.CompilationPass
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationCategory
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationConfig
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.PatternOptimizationPass
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.patterns.AssumeFalsePropagationPattern

class AssumeFalsePropagationPass @Inject constructor(
    config: OptimizationConfig,
    artifactManager: CompilationArtifactManager,
) : PatternOptimizationPass(
    config = config,
    category = OptimizationCategory.AssumeFalsePropagation,
    compilationPass = CompilationPass.AssumptionPropagation,
    patterns = listOf(AssumeFalsePropagationPattern()),
    artifactManager = artifactManager,
)
