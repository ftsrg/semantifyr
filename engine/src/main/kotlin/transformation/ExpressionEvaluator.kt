package hu.bme.mit.gamma.oxsts.engine.transformation

import hu.bme.mit.gamma.oxsts.engine.utils.dropLast
import hu.bme.mit.gamma.oxsts.engine.utils.isDataType
import hu.bme.mit.gamma.oxsts.engine.utils.onlyLast
import hu.bme.mit.gamma.oxsts.engine.utils.referencedElement
import hu.bme.mit.gamma.oxsts.model.oxsts.AndOperator
import hu.bme.mit.gamma.oxsts.model.oxsts.ChainReferenceExpression
import hu.bme.mit.gamma.oxsts.model.oxsts.EqualityOperator
import hu.bme.mit.gamma.oxsts.model.oxsts.Expression
import hu.bme.mit.gamma.oxsts.model.oxsts.Feature
import hu.bme.mit.gamma.oxsts.model.oxsts.GreaterThanOperator
import hu.bme.mit.gamma.oxsts.model.oxsts.GreaterThanOrEqualsOperator
import hu.bme.mit.gamma.oxsts.model.oxsts.InequalityOperator
import hu.bme.mit.gamma.oxsts.model.oxsts.LessThanOperator
import hu.bme.mit.gamma.oxsts.model.oxsts.LessThanOrEqualsOperator
import hu.bme.mit.gamma.oxsts.model.oxsts.LiteralBoolean
import hu.bme.mit.gamma.oxsts.model.oxsts.LiteralExpression
import hu.bme.mit.gamma.oxsts.model.oxsts.LiteralInteger
import hu.bme.mit.gamma.oxsts.model.oxsts.MinusOperator
import hu.bme.mit.gamma.oxsts.model.oxsts.NotOperator
import hu.bme.mit.gamma.oxsts.model.oxsts.OperatorExpression
import hu.bme.mit.gamma.oxsts.model.oxsts.OrOperator
import hu.bme.mit.gamma.oxsts.model.oxsts.PlusOperator
import hu.bme.mit.gamma.oxsts.model.oxsts.Reference
import hu.bme.mit.gamma.oxsts.model.oxsts.Transition

/**
 * This class evaluates compile-time evaluable expressions only!
 */
class ExpressionEvaluator(
    private val context: Instance
) {

    fun evaluateTransition(expression: Expression): Transition {
        require(expression is ChainReferenceExpression)

        val instance = evaluateInstanceReference(expression.dropLast(1))
        return instance.transitionResolver.evaluateTransition(expression.onlyLast())
    }

    fun evaluateInstanceReference(expression: Expression): Instance {
        require(expression is ChainReferenceExpression)

        return evaluateInstanceReferenceOrNull(expression) ?: error("Expression $expression feature in $context has no instances!")
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

    fun evaluateInstanceReferenceOrNull(expression: Expression): Instance? {
        val instanceSet = evaluateInstanceSetReference(expression)

        check(instanceSet.size <= 1) {
            "Expression $expression must refer to a singular feature!"
        }

        return instanceSet.singleOrNull()
    }

    fun evaluateInstanceSetReference(expression: Expression): Set<Instance> {
        require(expression is ChainReferenceExpression)

        return context.featureEvaluator.evaluateInstanceSet(expression)
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

    fun evaluateInstanceSet(expression: Expression): Set<Instance> {
        val result = evaluate(expression)

        check(result is InstanceData) {
            "Expression is not Instance set!"
        }

        return result.value
    }

    fun evaluateInstanceOrNull(expression: Expression): Instance? {
        val result = evaluateInstanceSet(expression)

        return result.singleOrNull()
    }

    fun evaluateInstance(expression: Expression): Instance {
        return evaluateInstanceOrNull(expression) ?: error("Feature is empty!")
    }

    fun evaluate(expression: Expression): DataType = when (expression) {
        is OperatorExpression -> evaluateOperator(expression)
        is LiteralExpression -> evaluateLiteral(expression)
        is ChainReferenceExpression -> evaluateReference(expression)
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

    private fun evaluateReference(expression: ChainReferenceExpression): DataType {
        if (expression.chains.lastOrNull()?.referencedElement() is Feature) {
            val feature = expression.chains.last().referencedElement() as Feature

            val context = evaluateInstanceReference(expression.dropLast(1))
            val actualFeature = RedefinitionHandler.resolveFeature(context.type, feature)

            if (actualFeature.isDataType) {
                check(actualFeature is Reference)

                return context.expressionEvaluator.evaluate(actualFeature.expression)
            }
        }

        return evaluateInstanceSetReference(expression).toInstanceData()
    }

}

sealed class DataType {

    override fun equals(other: Any?) = when (other) {
        is DataType -> dataEquals(other)
        else -> super.equals(other)
    }

    protected abstract fun dataEquals(other: DataType): Boolean

    override fun hashCode(): Int {
        return javaClass.hashCode()
    }

    abstract fun evaluateOperator(operator: OperatorExpression): DataType
    abstract fun evaluateOperator(operator: OperatorExpression, other: DataType): DataType

}

class IntegerData(
    val value: Int
) : DataType() {

    override fun dataEquals(other: DataType) = when (other) {
        is IntegerData -> value == other.value
        else -> false
    }

    override fun evaluateOperator(operator: OperatorExpression): DataType {
        error("Unary operators are not supported for Integers!")
    }

    override fun evaluateOperator(operator: OperatorExpression, other: DataType) = when (operator) {
        // Boolean operators
        is AndOperator -> error("")
        is OrOperator -> error("")
        is NotOperator -> error("")

        // Equality operators
        is EqualityOperator -> {
            require(other is IntegerData)

            (value == other.value).toBooleanData()
        }
        is InequalityOperator -> {
            require(other is IntegerData)

            (value != other.value).toBooleanData()
        }
        is GreaterThanOperator -> {
            require(other is IntegerData)

            (value > other.value).toBooleanData()
        }
        is GreaterThanOrEqualsOperator -> {
            require(other is IntegerData)

            (value >= other.value).toBooleanData()
        }
        is LessThanOperator -> {
            require(other is IntegerData)

            (value < other.value).toBooleanData()
        }
        is LessThanOrEqualsOperator -> {
            require(other is IntegerData)

            (value <= other.value).toBooleanData()
        }

        // Algebraic operators
        is PlusOperator -> {
            require(other is IntegerData)

            (value + other.value).toIntegerData()
        }
        is MinusOperator -> {
            require(other is IntegerData)

            (value - other.value).toIntegerData()
        }

        else -> error("Unknown type of operator expression: $operator")
    }

}

private fun Any.toIntegerData(): IntegerData {
    require(this is Int)

    return IntegerData(this)
}

class BooleanData(
    val value: Boolean
) : DataType() {

    override fun dataEquals(other: DataType) = when (other) {
        is BooleanData -> value == other.value
        else -> false
    }

    override fun evaluateOperator(operator: OperatorExpression) = when(operator) {
        // Boolean operators
        is AndOperator -> error("")
        is OrOperator -> error("")
        is NotOperator -> !value

        // Equality operators
        is EqualityOperator -> error("")
        is InequalityOperator -> error("")
        is GreaterThanOperator -> error("")
        is GreaterThanOrEqualsOperator -> error("")
        is LessThanOperator -> error("")
        is LessThanOrEqualsOperator -> error("")

        // Algebraic operators
        is PlusOperator -> error("")
        is MinusOperator -> error("")

        else -> error("Unsupported type of operator expression for boolean: $operator")
    }.toBooleanData()

    override fun evaluateOperator(operator: OperatorExpression, other: DataType) = when(operator) {
        // Boolean operators
        is AndOperator -> {
            require(other is BooleanData)

            value && other.value
        }
        is OrOperator -> {
            require(other is BooleanData)

            value || other.value
        }
        is NotOperator -> error("")

        // Equality operators
        is EqualityOperator -> {
            require(other is BooleanData)

            value == other.value
        }
        is InequalityOperator -> {
            require(other is BooleanData)

            value != other.value
        }
        is GreaterThanOperator -> error("")
        is GreaterThanOrEqualsOperator -> error("")
        is LessThanOperator -> error("")
        is LessThanOrEqualsOperator -> error("")

        // Algebraic operators
        is PlusOperator -> error("")
        is MinusOperator -> error("")

        else -> error("Unsupported type of operator expression for boolean: $operator")
    }.toBooleanData()

}

private fun Any.toBooleanData(): BooleanData {
    require(this is Boolean)

    return BooleanData(this)
}

class InstanceData(
    val value: Set<Instance>
) : DataType() {

    override fun dataEquals(other: DataType) = when (other) {
        is InstanceData -> value == other.value
        else -> false
    }

    override fun evaluateOperator(operator: OperatorExpression): DataType {
        error("Unary operators are not supported for Features!")
    }

    override fun evaluateOperator(operator: OperatorExpression, other: DataType) = when(operator) {
        // Boolean operators
        is AndOperator -> error("")
        is OrOperator -> error("")
        is NotOperator -> error("")

        // Equality operators
        is EqualityOperator -> {
            require(other is InstanceData)

            (value == other.value).toBooleanData()
        }
        is InequalityOperator -> {
            require(other is InstanceData)

            (value != other.value).toBooleanData()
        }
        is GreaterThanOperator -> {
            require(other is InstanceData)

            val greaterThen = value.containsAll(other.value)
            val notEquals = (value - other.value).any()

            (greaterThen && notEquals).toBooleanData()
        }
        is GreaterThanOrEqualsOperator -> {
            require(other is InstanceData)

            value.containsAll(other.value).toBooleanData()
        }
        is LessThanOperator -> {
            require(other is InstanceData)

            val lessThen = other.value.containsAll(value)
            val notEquals = (other.value - value).any()

            (lessThen && notEquals).toBooleanData()
        }
        is LessThanOrEqualsOperator -> {
            require(other is InstanceData)

            other.value.containsAll(value).toBooleanData()
        }

        // Algebraic operators
        is PlusOperator -> {
            require(other is InstanceData)

            (value + other.value).toInstanceData()
        }
        is MinusOperator -> {
            require(other is InstanceData)

            (value - other.value).toInstanceData()
        }

        else -> error("Unsupported type of operator expression for boolean: $operator")
    }

}

private fun Any.toInstanceData(): InstanceData {
    require(this is Set<*>)

    return InstanceData((this as Set<Instance>))
}
