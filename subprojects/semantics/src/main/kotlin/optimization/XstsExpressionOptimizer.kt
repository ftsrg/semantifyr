/*
 * SPDX-FileCopyrightText: 2023-2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.semantics.optimization

import com.google.inject.Inject
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.ConstantExpressionEvaluatorProvider
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.ExpressionEvaluation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.BooleanOp
import hu.bme.mit.semantifyr.oxsts.model.oxsts.BooleanOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Element
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralInteger
import hu.bme.mit.semantifyr.oxsts.model.oxsts.NegationOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.OperatorExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.UnaryOperator
import hu.bme.mit.semantifyr.semantics.expression.ConstantEvaluationTransformer
import hu.bme.mit.semantifyr.semantics.transformation.injection.scope.CompilationScoped
import hu.bme.mit.semantifyr.semantics.transformation.serializer.CompilationStateManager
import hu.bme.mit.semantifyr.semantics.utils.eAllOfType
import hu.bme.mit.semantifyr.semantics.utils.isConstantLiteralFalse
import hu.bme.mit.semantifyr.semantics.utils.isConstantLiteralTrue
import org.eclipse.xtext.EcoreUtil2

@CompilationScoped
class XstsExpressionOptimizer : AbstractLoopedOptimizer<Element>() {

    @Inject
    private lateinit var constantExpressionEvaluatorProvider: ConstantExpressionEvaluatorProvider

    @Inject
    private lateinit var compilationStateManager: CompilationStateManager

    @Inject
    private lateinit var constantEvaluationTransformer: ConstantEvaluationTransformer

    override fun doOptimizationStep(element: Element): Boolean {
        return rewriteConstantTrueOr(element)
            || rewriteConstantFalseAnd(element)
            || rewriteRedundantOr(element)
            || rewriteRedundantAnd(element)
            || rewriteDuplicateNegation(element)
            || rewriteConstantExpression(element)
    }

    // FIXME: replace with constant evaluator!
    private fun rewriteConstantExpression(element: Element): Boolean {
        val operatorResultPair = element.eAllOfType<OperatorExpression>().filterNot {
            it is UnaryOperator && it.body is LiteralInteger
        }.map {
            it to evaluateOrNull(it)
        }.firstOrNull {
            it.second != null
        }

        if (operatorResultPair == null) {
            return false
        }

        val (operator, evaluation) = operatorResultPair

        val constant = constantEvaluationTransformer.transformEvaluation(evaluation!!)

        EcoreUtil2.replace(operator, constant)

        compilationStateManager.commitModelState()

        return true
    }

    // FIXME: replace with constant evaluator!
    private fun rewriteConstantTrueOr(element: Element): Boolean {
        // any of its operands is true
        val constantTrueOr = element.eAllOfType<BooleanOperator>().firstOrNull {
            it.op == BooleanOp.OR && (it.left.isConstantLiteralTrue || it.right.isConstantLiteralTrue)
        }

        if (constantTrueOr == null) {
            return false
        }

        if (constantTrueOr.left.isConstantLiteralTrue) {
            EcoreUtil2.replace(constantTrueOr, constantTrueOr.left)
        } else {
            EcoreUtil2.replace(constantTrueOr, constantTrueOr.right)
        }

        compilationStateManager.commitModelState()

        return true
    }

    // FIXME: replace with constant evaluator!
    private fun rewriteConstantFalseAnd(element: Element): Boolean {
        // any of its operands is false
        val constantFalseAnd = element.eAllOfType<BooleanOperator>().firstOrNull {
            it.op == BooleanOp.AND && (it.left.isConstantLiteralFalse || it.right.isConstantLiteralFalse)
        }

        if (constantFalseAnd == null) {
            return false
        }

        if (constantFalseAnd.left.isConstantLiteralFalse) {
            EcoreUtil2.replace(constantFalseAnd, constantFalseAnd.left)
        } else {
            EcoreUtil2.replace(constantFalseAnd, constantFalseAnd.right)
        }

        compilationStateManager.commitModelState()

        return true
    }

    private fun rewriteRedundantOr(element: Element): Boolean {
        // or, that only depends on one of its arguments, the other is "false"
        val redundantOr = element.eAllOfType<BooleanOperator>().firstOrNull {
            it.op == BooleanOp.OR && (it.left.isConstantLiteralFalse || it.right.isConstantLiteralFalse)
        }

        if (redundantOr == null) {
            return false
        }

        if (redundantOr.left.isConstantLiteralFalse) {
            EcoreUtil2.replace(redundantOr, redundantOr.right)
        } else {
            EcoreUtil2.replace(redundantOr, redundantOr.left)
        }

        compilationStateManager.commitModelState()

        return true
    }

    private fun rewriteRedundantAnd(element: Element): Boolean {
        // and, that only depends on one of its arguments, the other is "true"
        val redundantAnd = element.eAllOfType<BooleanOperator>().firstOrNull {
            it.op == BooleanOp.AND && (it.left.isConstantLiteralTrue || it.right.isConstantLiteralTrue)
        }

        if (redundantAnd == null) {
            return false
        }

        if (redundantAnd.left.isConstantLiteralTrue) {
            EcoreUtil2.replace(redundantAnd, redundantAnd.right)
        } else {
            EcoreUtil2.replace(redundantAnd, redundantAnd.left)
        }

        compilationStateManager.commitModelState()

        return true
    }

    private fun rewriteDuplicateNegation(element: Element): Boolean {
        val redundantNegation = element.eAllOfType<NegationOperator>().firstOrNull {
            it.body is NegationOperator
        }

        if (redundantNegation == null) {
            return false
        }

        val internalNegation = redundantNegation.body as NegationOperator

        EcoreUtil2.replace(redundantNegation, internalNegation.body)

        compilationStateManager.commitModelState()

        return true
    }

    // FIXME: remove
    private fun evaluateOrNull(expression: Expression): ExpressionEvaluation? {
        return try {
            constantExpressionEvaluatorProvider.evaluate(expression)
        } catch (e: Exception) {
            null
        }
    }

}
