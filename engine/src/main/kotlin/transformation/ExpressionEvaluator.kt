package hu.bme.mit.gamma.oxsts.engine.transformation

import hu.bme.mit.gamma.oxsts.model.oxsts.AndOperator
import hu.bme.mit.gamma.oxsts.model.oxsts.ChainReferenceExpression
import hu.bme.mit.gamma.oxsts.model.oxsts.ChainingExpression
import hu.bme.mit.gamma.oxsts.model.oxsts.DeclarationReferenceExpression
import hu.bme.mit.gamma.oxsts.model.oxsts.Element
import hu.bme.mit.gamma.oxsts.model.oxsts.EqualityOperator
import hu.bme.mit.gamma.oxsts.model.oxsts.Expression
import hu.bme.mit.gamma.oxsts.model.oxsts.Feature
import hu.bme.mit.gamma.oxsts.model.oxsts.InequalityOperator
import hu.bme.mit.gamma.oxsts.model.oxsts.LiteralBoolean
import hu.bme.mit.gamma.oxsts.model.oxsts.LiteralExpression
import hu.bme.mit.gamma.oxsts.model.oxsts.LiteralNothing
import hu.bme.mit.gamma.oxsts.model.oxsts.LiteralSelf
import hu.bme.mit.gamma.oxsts.model.oxsts.NotOperator
import hu.bme.mit.gamma.oxsts.model.oxsts.OperatorExpression
import hu.bme.mit.gamma.oxsts.model.oxsts.OrOperator
import hu.bme.mit.gamma.oxsts.model.oxsts.ReferenceExpression

/**
 * This class evaluates compile-time evaluable expressions only!
 */
class ExpressionEvaluator(
    private val context: InstanceObject
) {
    fun evaluateBoolean(expression: Expression): Boolean = when (expression) {
        is OperatorExpression -> evaluateBooleanOperator(expression)
        is LiteralExpression -> evaluateBooleanLiteral(expression)
        else -> error("Unknown type of expression: $expression")
    }

    fun evaluateInstanceObject(expression: Expression) = evaluateInstanceObjectSet(expression).single()

    fun evaluateInstanceObjectSet(expression: Expression): List<InstanceObject> = when (expression) {
        is LiteralExpression -> listOf(evaluateLiteralToInstanceObject(expression))
        is ReferenceExpression -> evaluateReferenceToInstanceObjectSet(expression)
        else -> error("Unknown type of expression: $expression")
    }

    private fun evaluateBooleanOperator(operator: OperatorExpression): Boolean = when (operator) {
        is AndOperator -> evaluateBoolean(operator.operands[0]) && evaluateBoolean(operator.operands[1])
        is OrOperator -> evaluateBoolean(operator.operands[0]) || evaluateBoolean(operator.operands[1])
        is NotOperator -> !evaluateBoolean(operator.operands[0])
        is EqualityOperator -> evaluateInstanceObject(operator.operands[0]) == evaluateInstanceObject(operator.operands[1])
        is InequalityOperator -> evaluateInstanceObject(operator.operands[0]) != evaluateInstanceObject(operator.operands[1])
        else -> error("Unknown type of literal: $operator")
    }

    private fun evaluateBooleanLiteral(literal: LiteralExpression): Boolean = when (literal) {
        is LiteralBoolean -> literal.isValue
        else -> error("Unknown boolean type of literal: $literal")
    }

    private fun evaluateLiteralToInstanceObject(literal: LiteralExpression): InstanceObject = when (literal) {
        is LiteralNothing -> NothingInstance
        is LiteralSelf -> context
        else -> error("Unknown type of literal: $literal")
    }

    inline fun <reified T : Element> evaluateTypedReference(reference: ReferenceExpression): T {
        val element = evaluateReference(reference)

        check(element is T) {
            "Reference must point to element of type ${T::class.qualifiedName}"
        }

        return element
    }

    fun evaluateReference(reference: ReferenceExpression): Element {
        require(reference is ChainReferenceExpression)

        var localContext = context

        for (chain in reference.chains.dropLast(1)) {
            with(localContext.expressionEvaluator) {
                localContext = chain.evaluateToInstanceObjectSet().single()
            }
        }

        return with(localContext.expressionEvaluator) {
            reference.chains.last().evaluateReference()
        }
    }

    private fun ChainingExpression.evaluateReference(): Element {
        require(this is DeclarationReferenceExpression) {
            "Expression must be DeclarationReferenceExpression"
        }

        return element
    }

    private fun evaluateReferenceToInstanceObjectSet(reference: ReferenceExpression): List<InstanceObject> {
        require(reference is ChainReferenceExpression) {
            "Expression must be ChainReferenceExpression"
        }

        var localContext = listOf(context)
        for (chain in reference.chains) {
            with(localContext.single().expressionEvaluator) {
                localContext = chain.evaluateToInstanceObjectSet()
            }
        }
        return localContext
    }

    private fun ChainingExpression.evaluateToInstanceObjectSet(): List<InstanceObject> {
        require(this is DeclarationReferenceExpression) {
            error("Expression must be a DeclarationReferenceExpression!")
        }

        return evaluateToInstanceObjectSet()
    }

    private fun DeclarationReferenceExpression.evaluateToInstanceObjectSet(): List<InstanceObject> {
        require(element is Feature) {
            error("Expression must refer to a feature!")
        }

        return context.featureMap[element as Feature] ?: error("No instance for feature!")
    }

}
