/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.optimization.passes

import com.google.inject.Inject
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.CompilationArtifactManager
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.CompilationPass
import hu.bme.mit.semantifyr.compiler.pipeline.expression.ConstantExpressionEvaluationTransformer
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationCategory
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationConfig
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.PatternOptimizationPass
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.patterns.expression.ExpressionSimplificationPatterns
import hu.bme.mit.semantifyr.oxsts.lang.semantics.MultiplicityRangeEvaluator
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.ConstantExpressionEvaluatorProvider
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.MetaConstantExpressionEvaluatorProvider

class ExpressionSimplificationPass @Inject constructor(
    config: OptimizationConfig,
    evaluator: ConstantExpressionEvaluatorProvider,
    metaEvaluatorProvider: MetaConstantExpressionEvaluatorProvider,
    multiplicityRangeEvaluator: MultiplicityRangeEvaluator,
    transformer: ConstantExpressionEvaluationTransformer,
    artifactManager: CompilationArtifactManager,
) : PatternOptimizationPass(
    config = config,
    categories = listOf(
        OptimizationCategory.ExpressionSimplification,
        OptimizationCategory.ConstantFolding,
    ),
    compilationPass = CompilationPass.ExpressionSimplification,
    patterns = listOf(
        ExpressionSimplificationPatterns(
            evaluator,
            metaEvaluatorProvider,
            multiplicityRangeEvaluator,
            transformer,
        ),
    ),
    artifactManager = artifactManager,
)
