/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.optimization.patterns.expression

import hu.bme.mit.semantifyr.compiler.pipeline.expression.ConstantExpressionEvaluationTransformer
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationPattern
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.Worklist
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.ConstantExpressionEvaluatorProvider
import hu.bme.mit.semantifyr.oxsts.lang.utils.OxstsUtils
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ArithmeticBinaryOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ArithmeticUnaryOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.BooleanOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ComparisonOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralInteger
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralReal
import hu.bme.mit.semantifyr.oxsts.model.oxsts.NegationOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.UnaryOp
import org.eclipse.emf.ecore.EObject
import org.eclipse.xtext.EcoreUtil2

class ConstantFoldingPattern(
    private val evaluator: ConstantExpressionEvaluatorProvider,
    private val transformer: ConstantExpressionEvaluationTransformer,
) : OptimizationPattern {
    override val rootTypes = setOf(
        ArithmeticBinaryOperator::class.java,
        ComparisonOperator::class.java,
        BooleanOperator::class.java,
        ArithmeticUnaryOperator::class.java,
        NegationOperator::class.java,
    )

    override fun tryApply(element: EObject, worklist: Worklist<EObject>): Boolean {
        if (element !is Expression) {
            return false
        }
        if (!element.isCollapsibleOperator()) {
            return false
        }
        val parent = element.eContainer() ?: return false

        val evaluation = evaluator.evaluate(element)
        val constant = transformer.transformEvaluation(evaluation)
        EcoreUtil2.replace(element, constant)
        worklist.add(parent)
        return true
    }

    private fun Expression.isCollapsibleOperator(): Boolean {
        return when (this) {
            is ArithmeticBinaryOperator -> left.isFoldableLiteral() && right.isFoldableLiteral()
            is ComparisonOperator -> left.isFoldableLiteral() && right.isFoldableLiteral()
            is BooleanOperator -> left.isFoldableLiteral() && right.isFoldableLiteral()
            is ArithmeticUnaryOperator -> body.isFoldableLiteral() && !isSignedNumericLiteral()
            is NegationOperator -> body.isFoldableLiteral()
            else -> false
        }
    }

    private fun ArithmeticUnaryOperator.isSignedNumericLiteral(): Boolean {
        return op == UnaryOp.MINUS && (body is LiteralInteger || body is LiteralReal)
    }

    private fun Expression?.isFoldableLiteral(): Boolean {
        if (OxstsUtils.isSimpleExpression(this)) {
            return true
        }
        return this is ArithmeticUnaryOperator && isSignedNumericLiteral()
    }

}
