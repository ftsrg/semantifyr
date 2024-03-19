package hu.bme.mit.gamma.oxsts.engine.transformation

import hu.bme.mit.gamma.oxsts.engine.utils.dropLast
import hu.bme.mit.gamma.oxsts.model.oxsts.AndOperator
import hu.bme.mit.gamma.oxsts.model.oxsts.ChainReferenceExpression
import hu.bme.mit.gamma.oxsts.model.oxsts.EqualityOperator
import hu.bme.mit.gamma.oxsts.model.oxsts.Expression
import hu.bme.mit.gamma.oxsts.model.oxsts.InequalityOperator
import hu.bme.mit.gamma.oxsts.model.oxsts.LiteralBoolean
import hu.bme.mit.gamma.oxsts.model.oxsts.LiteralExpression
import hu.bme.mit.gamma.oxsts.model.oxsts.NotOperator
import hu.bme.mit.gamma.oxsts.model.oxsts.OperatorExpression
import hu.bme.mit.gamma.oxsts.model.oxsts.OrOperator
import hu.bme.mit.gamma.oxsts.model.oxsts.Transition

/**
 * This class evaluates compile-time evaluable expressions only!
 */
class ExpressionEvaluator(
    private val context: Instance
) {

    fun evaluateTransition(expression: Expression): Transition {
        require(expression is ChainReferenceExpression)

        val instance = evaluateInstance(expression.dropLast(1))
        return instance.transitionResolver.evaluateTransition(expression)
    }

    fun evaluateInstance(expression: Expression): Instance {
        require(expression is ChainReferenceExpression)

        return evaluateInstanceOrNull(expression) ?: error("Expression $expression feature in $context has no instances!")
    }

    fun findFirstValidContext(expression: Expression): Instance {
        try {
            evaluateInstance(expression)

            return context
        } catch (e: RuntimeException) {
            require(context.parent != null) {
                "Expression $expression could not be found in the Context tree!"
            }

            return context.parent.expressionEvaluator.findFirstValidContext(expression)
        }
    }

    fun evaluateInstanceBottomUp(expression: Expression): Instance {
        return try {
            evaluateInstance(expression)
        } catch (e: RuntimeException) {
            require(context.parent != null) {
                "Expression $expression could not be found in the Context tree!"
            }

            context.parent.expressionEvaluator.evaluateInstanceBottomUp(expression)
        }
    }

    fun evaluateInstanceOrNull(expression: Expression): Instance? {
        val instanceSet = evaluateInstanceSet(expression)

        check(instanceSet.size <= 1) {
            "Expression $expression must refer to a singular feature!"
        }

        return instanceSet.singleOrNull()
    }

    fun evaluateInstanceSet(expression: Expression): List<Instance> {
        require(expression is ChainReferenceExpression)

        return context.instanceEvaluator.evaluateInstanceSet(expression)
    }

    fun evaluateBoolean(expression: Expression): Boolean = when (expression) {
        is OperatorExpression -> evaluateBooleanOperator(expression)
        is LiteralExpression -> evaluateBooleanLiteral(expression)
        else -> error("Unknown type of expression: $expression")
    }

    private fun evaluateBooleanOperator(operator: OperatorExpression): Boolean = when (operator) {
        is AndOperator -> evaluateBoolean(operator.operands[0]) && evaluateBoolean(operator.operands[1])
        is OrOperator -> evaluateBoolean(operator.operands[0]) || evaluateBoolean(operator.operands[1])
        is NotOperator -> !evaluateBoolean(operator.operands[0])
        is EqualityOperator -> {
            val left = evaluateInstanceOrNull(operator.operands[0])
            val right = evaluateInstanceOrNull(operator.operands[1])

            left == right && left != null
        }
        is InequalityOperator -> {
            val left = evaluateInstanceOrNull(operator.operands[0])
            val right = evaluateInstanceOrNull(operator.operands[1])

            left != right
        }
        else -> error("Unknown type of literal: $operator")
    }

    private fun evaluateBooleanLiteral(literal: LiteralExpression): Boolean = when (literal) {
        is LiteralBoolean -> literal.isValue
        else -> error("Unknown boolean type of literal: $literal")
    }

}
