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
import hu.bme.mit.gamma.oxsts.model.oxsts.NotOperator
import hu.bme.mit.gamma.oxsts.model.oxsts.NothingReference
import hu.bme.mit.gamma.oxsts.model.oxsts.OperatorExpression
import hu.bme.mit.gamma.oxsts.model.oxsts.OrOperator
import hu.bme.mit.gamma.oxsts.model.oxsts.ReferenceExpression
import hu.bme.mit.gamma.oxsts.model.oxsts.SelfReference
import hu.bme.mit.gamma.oxsts.model.oxsts.Transition
import java.lang.IllegalStateException

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

    fun evaluateTransition(expression: Expression): Transition {
        require(expression is ChainReferenceExpression)

        val instanceObject = evaluateInstanceObject(expression.dropLast(1))
        return instanceObject.transitionEvaluator.evaluateTransition(expression)
    }

    fun evaluateInstanceObjectBottomUp(expression: Expression): InstanceObject {
        return try {
            evaluateInstanceObject(expression)
        } catch (e: RuntimeException) {
            require(context.parent != null) {
                "Expression $expression could not be found in the Context tree!"
            }

            context.parent.expressionEvaluator.evaluateInstanceObjectBottomUp(expression)
        }
    }

    fun evaluateInstanceObject(expression: Expression): InstanceObject {
        return evaluateInstanceObjectOrNull(expression) ?: error("Expression $expression feature has no instances!")
    }

    private fun evaluateInstanceObjectOrNull(expression: Expression): InstanceObject? {
        val instanceSet = evaluateInstanceObjectSet(expression)

        check(instanceSet.size <= 1) {
            "Expression $expression must refer to a singular feature!"
        }

        return instanceSet.singleOrNull()
    }

    fun evaluateInstanceObjectSet(expression: Expression): List<InstanceObject> = when (expression) {
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

    inline fun <reified T : Element> evaluateTypedReference(reference: ReferenceExpression): T {
        val element = evaluateReference(reference)

        check(element is T) {
            "Reference $reference must point to element of type ${T::class.qualifiedName}"
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
            "Expression $this must be DeclarationReferenceExpression"
        }

        return element
    }

    private fun evaluateReferenceToInstanceObjectSet(reference: ReferenceExpression): List<InstanceObject> {
        require(reference is ChainReferenceExpression) {
            "Expression $this must be ChainReferenceExpression"
        }

        var localContext = listOf(context)
        for (chain in reference.chains) {
            check(localContext.size == 1) {
                "Feature for $chain has ${localContext.size} elements!"
            }

            with(localContext.single().expressionEvaluator) {
                localContext = chain.evaluateToInstanceObjectSet()
            }
        }
        return localContext
    }

    private fun ChainingExpression.evaluateToInstanceObjectSet(): List<InstanceObject> {
        return when (this) {
            is NothingReference -> listOf(NothingInstance)
            is SelfReference -> listOf(context)
            is DeclarationReferenceExpression -> evaluateToInstanceObjectSet()
            else -> error("Expression $this must be a DeclarationReferenceExpression!")
        }
    }

    private fun DeclarationReferenceExpression.evaluateToInstanceObjectSet(): List<InstanceObject> {
        require(element is Feature) {
            error("Expression $this must refer to a feature!")
        }

        return context.featureMap[element] ?: emptyList()// error("No instance for feature $element!")
    }

}
