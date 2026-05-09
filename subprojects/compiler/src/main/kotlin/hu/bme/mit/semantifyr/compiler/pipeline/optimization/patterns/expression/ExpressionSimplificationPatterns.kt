/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.optimization.patterns.expression

import hu.bme.mit.semantifyr.compiler.pipeline.expression.ConstantExpressionEvaluationTransformer
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.CompositeOptimizationPattern
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationPattern
import hu.bme.mit.semantifyr.oxsts.lang.semantics.MultiplicityRangeEvaluator
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.ConstantExpressionEvaluatorProvider
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.MetaConstantExpressionEvaluatorProvider

class ExpressionSimplificationPatterns(
    evaluator: ConstantExpressionEvaluatorProvider,
    metaEvaluatorProvider: MetaConstantExpressionEvaluatorProvider,
    multiplicityRangeEvaluator: MultiplicityRangeEvaluator,
    transformer: ConstantExpressionEvaluationTransformer,
) : CompositeOptimizationPattern() {

    override val patterns: Collection<OptimizationPattern> = listOf(
        // Negation pushing
        NegationNormalizationPatterns(),
        DoubleUnaryMinusPattern(),
        NegatedComparisonPattern(),
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
        // If-then-else folding: constant guard and identical branches
        IfThenElseConstantGuardPattern(),
        IfThenElseIdenticalBranchesPattern(),
        // Type-driven comparisons against 'nothing'
        FeatureTypedNothingComparisonPattern(metaEvaluatorProvider, multiplicityRangeEvaluator),
        // Constant folding
        ConstantFoldingPattern(evaluator, transformer),
    )

}
