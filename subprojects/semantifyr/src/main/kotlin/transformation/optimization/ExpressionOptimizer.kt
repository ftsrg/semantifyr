/*
 * SPDX-FileCopyrightText: 2023-2024 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.semantifyr.transformation.optimization

import hu.bme.mit.semantifyr.oxsts.model.oxsts.AndOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.OperatorExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.OrOperator
import hu.bme.mit.semantifyr.oxsts.semantifyr.transformation.evaluation.BooleanData
import hu.bme.mit.semantifyr.oxsts.semantifyr.transformation.evaluation.IntegerData
import hu.bme.mit.semantifyr.oxsts.semantifyr.transformation.evaluation.SharedExpressionEvaluator
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.OxstsFactory
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.eAllContentsOfType
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.isConstantFalse
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.isConstantTrue
import org.eclipse.xtext.EcoreUtil2

object ExpressionOptimizer {

    fun Expression.optimize(): Boolean {
        var anyOptimized = false
        var optimized: Boolean

        do {
            optimized = optimizeInternal()
            if (optimized) {
                anyOptimized = true
            }
        } while (optimized)

        return anyOptimized
    }

    private fun Expression.optimizeInternal(): Boolean {
        return rewriteConstantTrueOr() ||
                rewriteConstantFalseAnd() ||
                rewriteRedundantOr() ||
                rewriteRedundantAnd() /*||
                evaluateConstantOperator()*/
    }

    private fun Expression.evaluateConstantOperator(): Boolean {
        val constantOperator = eAllContentsOfType<OperatorExpression>().firstOrNull {
            SharedExpressionEvaluator.isConstantEvaluable(it)
        }

        if (constantOperator == null) {
            return false
        }

        val result = SharedExpressionEvaluator.evaluate(constantOperator)

        val constant = when (result) {
            is BooleanData -> OxstsFactory.createLiteralBoolean(result.value)
            is IntegerData -> OxstsFactory.createLiteralInteger(result.value)
            else -> error("Incompatible result of constant operator: $constantOperator")
        }

        EcoreUtil2.replace(constantOperator, constant)

        return true
    }

    private fun Expression.rewriteConstantTrueOr(): Boolean {
        // any of its operands is true
        val constantTrueOr = eAllContentsOfType<OrOperator>().firstOrNull {
            it.operands.any { it.isConstantTrue }
        }

        if (constantTrueOr == null) {
            return false
        }

        EcoreUtil2.replace(constantTrueOr, OxstsFactory.createLiteralBoolean(true))

        return true
    }

    private fun Expression.rewriteConstantFalseAnd(): Boolean {
        // any of its operands is false
        val constantFalseAnd = eAllContentsOfType<AndOperator>().firstOrNull {
            it.operands.any { it.isConstantFalse }
        }

        if (constantFalseAnd == null) {
            return false
        }

        EcoreUtil2.replace(constantFalseAnd, OxstsFactory.createLiteralBoolean(false))

        return true
    }

    private fun Expression.rewriteRedundantOr(): Boolean {
        // or, that only depends on one of its arguments, the other is "false"
        val redundantOr = eAllContentsOfType<OrOperator>().firstOrNull {
            it.operands.any { it.isConstantFalse }
        }

        if (redundantOr == null) {
            return false
        }

        val operand = redundantOr.operands.first { it.isConstantFalse }
        replaceRedundantOperand(redundantOr, operand)

        return true
    }

    private fun Expression.rewriteRedundantAnd(): Boolean {
        // and, that only depends on one of its arguments, the other is "true"
        val redundantAnd = eAllContentsOfType<AndOperator>().firstOrNull {
            it.operands.any { it.isConstantTrue }
        }

        if (redundantAnd == null) {
            return false
        }

        val operand = redundantAnd.operands.first { it.isConstantTrue }
        replaceRedundantOperand(redundantAnd, operand)

        return true
    }

    private fun replaceRedundantOperand(operator: OperatorExpression, operand: Expression) {
        val operandIndex = operator.operands.indexOf(operand)
        val otherOperandIndex = 1 - operandIndex
        val otherOperand = operator.operands[otherOperandIndex]

        EcoreUtil2.replace(operator, otherOperand)
    }

}
