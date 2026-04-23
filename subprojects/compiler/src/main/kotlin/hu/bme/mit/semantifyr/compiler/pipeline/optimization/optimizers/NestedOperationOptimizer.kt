/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.optimization.optimizers

import com.google.inject.Inject
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.CompilationArtifactManager
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.CompilationPass
import hu.bme.mit.semantifyr.compiler.pipeline.expression.ConstantExpressionEvaluationTransformer
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.Optimizer
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.PatternOptimizer
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.patterns.AssumeFalsePropagationPattern
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.patterns.FlatteningPattern
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.patterns.RedundancyPatterns
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.patterns.expression.ExpressionSimplificationPatterns
import hu.bme.mit.semantifyr.oxsts.lang.semantics.MultiplicityRangeEvaluator
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.ConstantExpressionEvaluatorProvider
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.MetaConstantExpressionEvaluatorProvider
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Operation

class NestedOperationOptimizer @Inject constructor(
    evaluator: ConstantExpressionEvaluatorProvider,
    metaEvaluatorProvider: MetaConstantExpressionEvaluatorProvider,
    multiplicityRangeEvaluator: MultiplicityRangeEvaluator,
    transformer: ConstantExpressionEvaluationTransformer,
    artifactManager: CompilationArtifactManager,
) : Optimizer<Operation>() {

    private val patternOptimizer = PatternOptimizer(
        patterns = listOf(
            AssumeFalsePropagationPattern(),
            FlatteningPattern(),
            RedundancyPatterns(),
            ExpressionSimplificationPatterns(
                evaluator,
                metaEvaluatorProvider,
                multiplicityRangeEvaluator,
                transformer,
            ),
        ),
        pass = CompilationPass.OperationCallInlining,
        artifactManager = artifactManager,
    )

    override fun optimize(input: Operation): Boolean {
        return patternOptimizer.optimize(input)
    }

}
