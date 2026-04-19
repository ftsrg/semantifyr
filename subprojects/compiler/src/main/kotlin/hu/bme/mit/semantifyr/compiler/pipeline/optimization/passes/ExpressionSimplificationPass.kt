/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.optimization.passes

import com.google.inject.Inject
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.ConstantExpressionEvaluatorProvider
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.CompilationArtifactManager
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.CompilationPass
import hu.bme.mit.semantifyr.compiler.pipeline.context.InstantiatedCompilationContext
import hu.bme.mit.semantifyr.compiler.pipeline.expression.ConstantExpressionEvaluationTransformer
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.AnalysisManager
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationCategory
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationConfig
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.Pass
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.PassResult
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.WorklistOptimizer
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.patterns.expression.ArithmeticIdentityPattern
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.patterns.expression.BubbleNotAGPattern
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.patterns.expression.BubbleNotEFPattern
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.patterns.expression.ConstantFalseAndPattern
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.patterns.expression.ConstantTrueOrPattern
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.patterns.expression.DeMorganPattern
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.patterns.expression.DoubleNegationPattern
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.patterns.expression.DoubleUnaryMinusPattern
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.patterns.expression.IdempotentBooleanPattern
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.patterns.expression.NegatedComparisonPattern
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.patterns.expression.RedundantAndPattern
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.patterns.expression.RedundantOrPattern
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.patterns.expression.SelfArithmeticPattern
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.patterns.expression.SelfComparisonPattern
import hu.bme.mit.semantifyr.semantics.compilation.optimization.patterns.expression.ConstantFoldingPattern

class ExpressionSimplificationPass @Inject constructor(
    private val config: OptimizationConfig,
    evaluator: ConstantExpressionEvaluatorProvider,
    transformer: ConstantExpressionEvaluationTransformer,
    artifactManager: CompilationArtifactManager,
) : Pass<InstantiatedCompilationContext> {

    private val worklistOptimizer = WorklistOptimizer(
        patterns = listOf(
            // Double-inverse / negation pushing first - creates opportunities for others
            DoubleNegationPattern(),
            DoubleUnaryMinusPattern(),
            NegatedComparisonPattern(),
            DeMorganPattern(),
            // Identity / absorption with constants
            ConstantTrueOrPattern(),
            ConstantFalseAndPattern(),
            RedundantOrPattern(),
            RedundantAndPattern(),
            ArithmeticIdentityPattern(),
            // Self-operand / structural
            IdempotentBooleanPattern(),
            SelfComparisonPattern(),
            SelfArithmeticPattern(),
            // Temporal operator rewrites
            BubbleNotEFPattern(),
            BubbleNotAGPattern(),
            // Constant folding last - tries to evaluate whatever is left
            ConstantFoldingPattern(evaluator, transformer),
        ),
        pass = CompilationPass.ExpressionSimplification,
        artifactManager = artifactManager,
    )

    override fun run(input: InstantiatedCompilationContext, analyses: AnalysisManager): PassResult {
        if (!config.isAnyEnabled(OptimizationCategory.ExpressionSimplification, OptimizationCategory.ConstantFolding)) {
            return PassResult.Unchanged
        }
        val changed = worklistOptimizer.optimize(input.inlinedOxsts)
        return if (changed) PassResult.changed() else PassResult.Unchanged
    }

}
