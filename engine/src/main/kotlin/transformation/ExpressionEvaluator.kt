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
 * This class evaluates compile-time evaluatable expressions only!
 */
class ExpressionEvaluator(
    val context: InstanceObject
) {

    fun evaluateBoolean(expression: Expression): Boolean = when (expression) {
        is OperatorExpression -> evaluateOperator(expression)
        is LiteralExpression -> evaluateLiteral(expression)
        else -> error("Expression is not of known type: $expression") // FIXME
    }

    fun evaluateInstanceObject(expression: Expression): InstanceObject = when (expression) {
        is LiteralExpression -> evaluateLiteralToInstanceObject(expression)
        is ReferenceExpression -> evaluateReferenceToInstanceObject(expression)
        else -> error("Expression is not of known type: $expression") // FIXME
    }

    fun evaluateOperator(operator: OperatorExpression): Boolean = when(operator) {
        is AndOperator -> evaluateBoolean(operator.operands[0]) && evaluateBoolean(operator.operands[1])
        is OrOperator -> evaluateBoolean(operator.operands[0]) || evaluateBoolean(operator.operands[1])
        is NotOperator -> !evaluateBoolean(operator.operands[0])
        is EqualityOperator -> evaluateInstanceObject(operator.operands[0]) == evaluateInstanceObject(operator.operands[1])
        is InequalityOperator -> evaluateInstanceObject(operator.operands[0]) != evaluateInstanceObject(operator.operands[1])
        else -> error("Operator is not of known type: $operator") // FIXME
    }

    fun evaluateLiteral(literal: LiteralExpression): Boolean = when(literal) {
        is LiteralBoolean -> literal.isValue
        else -> error("Operator is not of known type: $literal") // FIXME
    }


    fun evaluateLiteralToInstanceObject(literal: LiteralExpression): InstanceObject = when (literal) {
        is LiteralNothing -> NothingInstance
        is LiteralSelf -> context
        else-> error("Literal is not of known type: $literal") // FIXME
    }

    inline fun <reified T : Element> evaluateTypedReference(reference: ReferenceExpression): T {
        return evaluateReference(reference) as? T ?: error("Reference must point to element of type ${T::class.qualifiedName}")
    }

    fun evaluateReference(reference: ReferenceExpression): Element = when(reference) {
        is ChainReferenceExpression -> {
            var localContext = context

            for (chain in reference.chains.dropLast(1)) {
                with(localContext.expressionEvaluator) {
                    localContext = chain.evaluateToInstanceObject()
                }
            }

            with(localContext.expressionEvaluator) {
                reference.chains.last().element
            }
        }
        is DeclarationReferenceExpression -> reference.element
        is ChainingExpression -> reference.element
        else -> error("Reference is not of known type: $reference") // FIXME
    }

    fun evaluateReferenceToInstanceObject(reference: ReferenceExpression): InstanceObject = when (reference) {
        is ChainReferenceExpression -> {
            var localContext = context
            for (chain in reference.chains) {
                with(localContext.expressionEvaluator) {
                    localContext = chain.evaluateToInstanceObject()
                }
            }
            localContext
        }
        is DeclarationReferenceExpression -> reference.evaluateToInstanceObject()
        is ChainingExpression -> reference.evaluateToInstanceObject()
        else -> error("Reference is not of known type: $reference")
    }

    private fun ChainingExpression.evaluateToInstanceObject(): InstanceObject {
        if (element !is Feature) {
            error("Expression must refer to a feature!")
        }

        return context.featureMap[element as Feature] ?: error("No instance for feature!")
    }

    private fun DeclarationReferenceExpression.evaluateToInstanceObject(): InstanceObject {
        if (element !is Feature) {
            error("Expression must refer to a feature!")
        }

        return context.featureMap[element as Feature] ?: error("No instance for feature!")
    }

}
