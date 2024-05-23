/*
 * SPDX-FileCopyrightText: 2023-2024 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.engine.transformation.evaluation

import hu.bme.mit.semantifyr.oxsts.engine.utils.NothingInstance
import hu.bme.mit.semantifyr.oxsts.engine.utils.except
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AndOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.EqualityOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.GreaterThanOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.GreaterThanOrEqualsOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InequalityOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Instance
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LessThanOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LessThanOrEqualsOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.MinusOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.NotOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.OperatorExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.OrOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.PlusOperator

sealed class DataType {

    override fun equals(other: Any?) = when (other) {
        is DataType -> dataEquals(other)
        else -> super.equals(other)
    }

    protected abstract fun dataEquals(other: DataType): Boolean

    override fun hashCode(): Int {
        return javaClass.hashCode()
    }

    // TODO: should we extract these methods into evaluator classes?
    //  Seems a little out of place here
    abstract fun evaluateOperator(operator: OperatorExpression): DataType
    abstract fun evaluateOperator(operator: OperatorExpression, other: DataType): DataType

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

class InstanceData(
    val value: Set<Instance>
) : DataType() {

    val isSetSemantics = value.isEmpty() || value.size >= 2

    override fun dataEquals(other: DataType) = when (other) {
        is InstanceData -> value == other.value
        else -> false
    }

    override fun evaluateOperator(operator: OperatorExpression): DataType {
        error("Unary operators are not supported for Features!")
    }

    override fun evaluateOperator(operator: OperatorExpression, other: DataType): DataType {
        require(other is InstanceData)

        val otherValue = if (isSetSemantics) {
            other.value.except(NothingInstance)
        } else {
            other.value
        }

        return when(operator) {
            // Boolean operators
            is AndOperator -> error("Unsupported type of operator expression for boolean: $operator")
            is OrOperator -> error("Unsupported type of operator expression for boolean: $operator")
            is NotOperator -> error("Unsupported type of operator expression for boolean: $operator")

            // Equality operators
            is EqualityOperator -> {
                (value == otherValue).toBooleanData()
            }
            is InequalityOperator -> {
                (value != otherValue).toBooleanData()
            }
            is GreaterThanOperator -> {
                val greaterThen = value.containsAll(otherValue)
                val notEquals = (value - otherValue.toSet()).any()

                (greaterThen && notEquals).toBooleanData()
            }
            is GreaterThanOrEqualsOperator -> {
                value.containsAll(other.value).toBooleanData()
            }
            is LessThanOperator -> {
                val lessThen = other.value.containsAll(value)
                val notEquals = (other.value - value).any()

                (lessThen && notEquals).toBooleanData()
            }
            is LessThanOrEqualsOperator -> {
                other.value.containsAll(value).toBooleanData()
            }

            // Algebraic operators
            is PlusOperator -> {
                (value + other.value).toInstanceData()
            }
            is MinusOperator -> {
                (value - other.value).toInstanceData()
            }

            else -> error("Unsupported type of operator expression for boolean: $operator")
        }
    }

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

private fun Any.toBooleanData(): BooleanData {
    require(this is Boolean)

    return BooleanData(this)
}

private fun Any.toInstanceData(): InstanceData {
    require(this is Set<*>)

    @Suppress("UNCHECKED_CAST") // we can be sure * is Instance in this case
    return InstanceData((this as Set<Instance>))
}
