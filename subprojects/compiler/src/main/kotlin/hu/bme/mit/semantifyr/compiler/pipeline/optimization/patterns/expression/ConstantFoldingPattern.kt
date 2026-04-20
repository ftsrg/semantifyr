/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.semantics.compilation.optimization.patterns.expression

import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.ConstantExpressionEvaluatorProvider
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ArithmeticBinaryOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ArithmeticUnaryOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.BooleanOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ComparisonOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralInteger
import hu.bme.mit.semantifyr.oxsts.model.oxsts.NegationOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.UnaryOp
import hu.bme.mit.semantifyr.compiler.pipeline.expression.ConstantExpressionEvaluationTransformer
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationPattern
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.Worklist
import org.eclipse.emf.ecore.EObject
import org.eclipse.xtext.EcoreUtil2

/**
 * Folds an operator whose operands are all literals into a single literal.
 *
 * The match is a positive type-directed check: we fold only the operator
 * classes that collapse to a strictly simpler form (scalar literal). The
 * recursion terminates naturally because the result of folding is a
 * [LiteralExpression], and [LiteralExpression]s never match this pattern.
 *
 * Excluded on purpose:
 * - [hu.bme.mit.semantifyr.oxsts.model.oxsts.RangeExpression]: folding a
 *   range with literal bounds produces another range with literal bounds -
 *   structurally identical, just a different EObject. Not worth replacing.
 * - [hu.bme.mit.semantifyr.oxsts.model.oxsts.TemporalOperator]: property-level
 *   wrapper, must survive intact for the backend.
 *
 * Operator collapses that ARE handled here (children must be
 * [LiteralExpression] in the strict sense): [ArithmeticBinaryOperator],
 * [ArithmeticUnaryOperator], [BooleanOperator], [ComparisonOperator],
 * [NegationOperator].
 *
 * Since the match predicate already guarantees evaluability, the evaluator
 * call here is expected to succeed. A failure would be a compiler bug (type
 * mismatch that the Xtext validator should have caught) and is allowed to
 * propagate.
 */
class ConstantFoldingPattern(
    private val evaluator: ConstantExpressionEvaluatorProvider,
    private val transformer: ConstantExpressionEvaluationTransformer,
) : OptimizationPattern {

    override fun tryApply(element: EObject, worklist: Worklist<EObject>): Boolean {
        if (element !is Expression) return false
        if (!element.isCollapsibleOperator()) return false

        val evaluation = evaluator.evaluate(element)
        val constant = transformer.transformEvaluation(evaluation)
        val parent = element.eContainer() ?: return false

        EcoreUtil2.replace(element, constant)
        worklist.add(parent)
        return true
    }

    /**
     * True iff [this] is one of the operator kinds that collapses to a scalar
     * literal and all its direct operand [Expression]s are already literals.
     * Excludes [hu.bme.mit.semantifyr.oxsts.model.oxsts.RangeExpression] and
     * [hu.bme.mit.semantifyr.oxsts.model.oxsts.TemporalOperator] - those stay
     * in their operator form even with literal operands.
     *
     * Also excludes canonical negative integer literals
     * `-N = ArithmeticUnaryOperator(MINUS, LiteralInteger(N))`. The grammar's
     * INT token is non-negative, so negative integers are encoded as a unary
     * minus on a positive literal. "Folding" one of these would produce the
     * same structure back and cause an infinite rewrite loop.
     */
    private fun Expression.isCollapsibleOperator(): Boolean {
        return when (this) {
            is ArithmeticBinaryOperator -> left.isLiteral() && right.isLiteral()
            is ComparisonOperator -> left.isLiteral() && right.isLiteral()
            is BooleanOperator -> left.isLiteral() && right.isLiteral()
            is ArithmeticUnaryOperator -> body.isLiteral() && !isCanonicalNegativeInteger()
            is NegationOperator -> body.isLiteral()
            else -> false
        }
    }

    private fun ArithmeticUnaryOperator.isCanonicalNegativeInteger(): Boolean {
        return op == UnaryOp.MINUS && body is LiteralInteger
    }

    private fun Expression?.isLiteral(): Boolean {
        return this is LiteralExpression
    }

}
