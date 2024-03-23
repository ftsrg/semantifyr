package hu.bme.mit.gamma.oxsts.engine.transformation

import hu.bme.mit.gamma.oxsts.engine.utils.dropLast
import hu.bme.mit.gamma.oxsts.engine.utils.lastChain
import hu.bme.mit.gamma.oxsts.engine.utils.onlyLast
import hu.bme.mit.gamma.oxsts.model.oxsts.ChainReferenceExpression
import hu.bme.mit.gamma.oxsts.model.oxsts.Expression
import hu.bme.mit.gamma.oxsts.model.oxsts.LiteralBoolean
import hu.bme.mit.gamma.oxsts.model.oxsts.LiteralExpression
import hu.bme.mit.gamma.oxsts.model.oxsts.LiteralInteger
import hu.bme.mit.gamma.oxsts.model.oxsts.OperatorExpression
import hu.bme.mit.gamma.oxsts.model.oxsts.Transition

/**
 * This class evaluates compile-time evaluable expressions only!
 */
class ExpressionEvaluator(
    private val context: Instance
) {

    fun evaluateTransition(expression: Expression): Transition {
        require(expression is ChainReferenceExpression)

        val context = evaluateInstance(expression.dropLast(1))
        return context.transitionResolver.resolveTransition(expression.lastChain())
    }

    fun findFirstValidContext(expression: Expression): Instance {
        try {
            evaluate(expression)

            return context
        } catch (e: RuntimeException) {
            require(context.parent != null) {
                "Expression $expression could not be found in the Context tree!"
            }

            return context.parent.expressionEvaluator.findFirstValidContext(expression)
        }
    }

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

    fun evaluate(expression: Expression): DataType = when (expression) {
        is OperatorExpression -> evaluateOperator(expression)
        is LiteralExpression -> evaluateLiteral(expression)
        is ChainReferenceExpression -> context.featureEvaluator.evaluate(expression)
        else -> error("Unknown type of expression: $expression")
    }

    private fun evaluateOperator(operator: OperatorExpression): DataType {
        if (operator.operands.size == 1) {
            return evaluate(operator.operands[0]).evaluateOperator(operator)
        }

        return evaluate(operator.operands[0]).evaluateOperator(operator, evaluate(operator.operands[1]))
    }

    private fun evaluateLiteral(literal: LiteralExpression): DataType = when (literal) {
        is LiteralBoolean -> BooleanData(literal.isValue)
        is LiteralInteger -> IntegerData(literal.value)
        else -> error("Unknown boolean type of literal: $literal")
    }

}
