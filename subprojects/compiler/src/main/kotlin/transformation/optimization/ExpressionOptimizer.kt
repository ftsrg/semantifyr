/*
 * SPDX-FileCopyrightText: 2023-2024 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.compiler.transformation.optimization

import hu.bme.mit.semantifyr.oxsts.compiler.transformation.evaluation.BooleanData
import hu.bme.mit.semantifyr.oxsts.compiler.transformation.evaluation.IntegerData
import hu.bme.mit.semantifyr.oxsts.compiler.transformation.evaluation.SharedExpressionEvaluator
import hu.bme.mit.semantifyr.oxsts.compiler.utils.OxstsFactory
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AndOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralBoolean
import hu.bme.mit.semantifyr.oxsts.model.oxsts.OperatorExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.OrOperator
import org.eclipse.xtext.EcoreUtil2

object ExpressionOptimizer {

    fun optimize(expression: Expression): Boolean {
        var anyOptimized = false
        var optimized: Boolean

        do {
            optimized = expression.optimizeInternal()
            anyOptimized = anyOptimized || optimized
        } while (optimized)

        return anyOptimized
    }

    private fun Expression.optimizeInternal(): Boolean {
        return rewriteTrueOr() ||
                rewriteFalseAnd() ||
                rewriteRedundantOr() ||
                rewriteRedundantAnd() ||
                evaluateConstantOperator()
    }

    private fun Expression.evaluateConstantOperator(): Boolean {
        // any of its operands is true
        val constantOperator = EcoreUtil2.getAllContentsOfType(this, OperatorExpression::class.java).firstOrNull {
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

    private fun Expression.rewriteTrueOr(): Boolean {
        // any of its operands is true
        val trueOr = EcoreUtil2.getAllContentsOfType(this, OrOperator::class.java).firstOrNull {
            it.operands.any {
                it is LiteralBoolean && it.isValue
            }
        }

        if (trueOr == null) {
            return false
        }

        EcoreUtil2.replace(trueOr, OxstsFactory.createLiteralBoolean(true))

        return true
    }

    private fun Expression.rewriteFalseAnd(): Boolean {
        // any of its operands is false
        val falseAnd = EcoreUtil2.getAllContentsOfType(this, AndOperator::class.java).firstOrNull {
            it.operands.any {
                it is LiteralBoolean && !it.isValue
            }
        }

        if (falseAnd == null) {
            return false
        }

        EcoreUtil2.replace(falseAnd, OxstsFactory.createLiteralBoolean(true))

        return true
    }

    private fun Expression.rewriteRedundantOr(): Boolean {
        // or, that only depends on one of its arguments, the other is "false"
        val redundantOr = EcoreUtil2.getAllContentsOfType(this, OrOperator::class.java).firstOrNull {
            it.operands.any {
                it is LiteralBoolean && !it.isValue
            }
        }

        if (redundantOr == null) {
            return false
        }

        val operand = redundantOr.operands.first {
            it is LiteralBoolean && !it.isValue
        }
        val operandIndex = redundantOr.operands.indexOf(operand)
        val otherOperandIndex = 1 - operandIndex
        val otherOperand = redundantOr.operands[otherOperandIndex]

        EcoreUtil2.replace(redundantOr, otherOperand)

        return true
    }

    private fun Expression.rewriteRedundantAnd(): Boolean {
        // and, that only depends on one of its arguments, the other is "true"
        val redundantAnd = EcoreUtil2.getAllContentsOfType(this, AndOperator::class.java).firstOrNull {
            it.operands.any {
                it is LiteralBoolean && it.isValue
            }
        }

        if (redundantAnd == null) {
            return false
        }

        val operand = redundantAnd.operands.first {
            it is LiteralBoolean && it.isValue
        }
        val operandIndex = redundantAnd.operands.indexOf(operand)
        val otherOperandIndex = 1 - operandIndex
        val otherOperand = redundantAnd.operands[otherOperandIndex]

        EcoreUtil2.replace(redundantAnd, otherOperand)

        return true
    }

}
