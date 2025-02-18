/*
 * SPDX-FileCopyrightText: 2023-2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.semantifyr.transformation.evaluation

import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Instance
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralBoolean
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralInteger
import hu.bme.mit.semantifyr.oxsts.model.oxsts.OperatorExpression

open class ExpressionEvaluator {

    fun evaluateBoolean(expression: Expression): Boolean {
        val result = evaluate(expression)

        check(result is BooleanData) {
            "Expression is not Boolean!"
        }

        return result.value
    }

    fun evaluateInteger(expression: Expression): Int {
        val result = evaluate(expression)

        check(result is IntegerData) {
            "Expression is not Integer!"
        }

        return result.value
    }

    fun evaluateInstance(expression: Expression): Instance {
        return evaluateInstanceOrNull(expression) ?: error("Feature is empty!")
    }

    fun evaluateInstanceOrNull(expression: Expression): Instance? {
        val result = evaluateInstanceSet(expression)

        return result.singleOrNull()
    }

    fun evaluateInstanceSet(expression: Expression): Set<Instance> {
        val result = evaluate(expression)

        check(result is InstanceData) {
            "Expression is not Instance set!"
        }

        return result.value
    }

    protected fun evaluateOperator(operator: OperatorExpression): DataType {
        if (operator.operands.size == 1) {
            return evaluate(operator.operands[0]).evaluateOperator(operator)
        }

        return evaluate(operator.operands[0]).evaluateOperator(operator, evaluate(operator.operands[1]))
    }

    protected fun evaluateLiteral(literal: LiteralExpression): DataType = when (literal) {
        is LiteralBoolean -> BooleanData(literal.isValue)
        is LiteralInteger -> IntegerData(literal.value)
        else -> error("Unknown boolean type of literal: $literal")
    }

    open fun evaluate(expression: Expression): DataType = when (expression) {
        is OperatorExpression -> evaluateOperator(expression)
        is LiteralExpression -> evaluateLiteral(expression)
        else -> error("Unknown type of expression: $expression")
    }

}

fun ExpressionEvaluator.evaluateOrNull(expression: Expression): DataType? {
    return try {
        evaluate(expression)
    } catch (_: Exception) {
        null
    }
}

object SharedExpressionEvaluator : ExpressionEvaluator() {

    fun isConstantEvaluable(expression: Expression): Boolean {
        // FIXME: could be smarter about this, this is wasteful
        try {
            evaluate(expression)
            return true
        } catch (_: Exception) {
            return false
        }
    }

}
