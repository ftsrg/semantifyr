/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.theta.backannotation

import com.google.inject.Inject
import hu.bme.mit.semantifyr.backends.theta.transformation.xsts.ThetaDomainTransformer
import hu.bme.mit.semantifyr.compiler.pipeline.utils.OxstsFactory
import hu.bme.mit.semantifyr.oxsts.lang.semantics.MultiplicityRangeEvaluator
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.RangeEvaluation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration
import hu.bme.mit.semantifyr.xsts.lang.utils.XstsExpressionVisitor
import hu.bme.mit.semantifyr.xsts.lang.xsts.ArithmeticBinaryOperator
import hu.bme.mit.semantifyr.xsts.lang.xsts.ArithmeticOp
import hu.bme.mit.semantifyr.xsts.lang.xsts.ArithmeticUnaryOperator
import hu.bme.mit.semantifyr.xsts.lang.xsts.BooleanOp
import hu.bme.mit.semantifyr.xsts.lang.xsts.BooleanOperator
import hu.bme.mit.semantifyr.xsts.lang.xsts.ComparisonOp
import hu.bme.mit.semantifyr.xsts.lang.xsts.ComparisonOperator
import hu.bme.mit.semantifyr.xsts.lang.xsts.ConcreteLiteralArray
import hu.bme.mit.semantifyr.xsts.lang.xsts.DefaultLiteralArray
import hu.bme.mit.semantifyr.xsts.lang.xsts.ElementReferenceExpression
import hu.bme.mit.semantifyr.xsts.lang.xsts.EnumLiteral
import hu.bme.mit.semantifyr.xsts.lang.xsts.IfThenElseExpression
import hu.bme.mit.semantifyr.xsts.lang.xsts.IntegerType
import hu.bme.mit.semantifyr.xsts.lang.xsts.LiteralBoolean
import hu.bme.mit.semantifyr.xsts.lang.xsts.LiteralInteger
import hu.bme.mit.semantifyr.xsts.lang.xsts.NegationOperator
import hu.bme.mit.semantifyr.xsts.lang.xsts.ReadIndexingSuffixExpression
import hu.bme.mit.semantifyr.xsts.lang.xsts.Type
import hu.bme.mit.semantifyr.xsts.lang.xsts.UnaryOp
import hu.bme.mit.semantifyr.xsts.lang.xsts.WriteIndexingSuffixExpression
import java.math.BigInteger

private typealias XstsExpression = hu.bme.mit.semantifyr.xsts.lang.xsts.Expression

private sealed interface XstsValue

private data class IntegerValue(val value: BigInteger) : XstsValue

private data class BooleanValue(val value: Boolean) : XstsValue

private data class EnumValue(val literal: EnumLiteral) : XstsValue

private data class ArrayValue(val elements: Map<BigInteger, XstsValue>, val default: XstsValue) : XstsValue

// a witness value has no free variables, so it is evaluated as Theta evaluates it and written as a literal
class XstsExpressionToOxstsExpressionTransformer @Inject constructor(
    private val thetaDomainTransformer: ThetaDomainTransformer,
    private val multiplicityRangeEvaluator: MultiplicityRangeEvaluator,
) {

    fun transform(expression: XstsExpression, variable: VariableDeclaration): Expression {
        val value = XstsValueEvaluator.evaluate(expression)
        if (value !is ArrayValue) {
            return literal(value)
        }
        val typeSpecification = variable.typeSpecification?.takeIf {
            it.multiplicity != null
        } ?: error("Theta's counterexample holds an array for the variable ${variable.name}, which is not an array")
        val slotCount = multiplicityRangeEvaluator.evaluate(typeSpecification).upperBound
        if (slotCount == RangeEvaluation.INFINITY) {
            throw UnrepresentableWitnessValueException("Theta's counterexample holds an array for ${variable.name}, whose size is unbounded")
        }
        return OxstsFactory.createArrayLiteral().also {
            for (index in 0..<slotCount) {
                it.values += literal(value.elements[index.toBigInteger()] ?: value.default)
            }
        }
    }

    private fun literal(value: XstsValue): Expression {
        return when (value) {
            is IntegerValue -> {
                if (!value.value.fitsInt()) {
                    throw UnrepresentableWitnessValueException("Theta's counterexample holds ${value.value}, beyond the 32-bit integers of OXSTS")
                }
                OxstsFactory.createLiteralInteger(value.value.toInt())
            }
            is BooleanValue -> OxstsFactory.createLiteralBoolean(value.value)
            is EnumValue -> OxstsFactory.createElementReference(thetaDomainTransformer.getOriginal(value.literal))
            is ArrayValue -> error("Theta's counterexample holds an array of arrays, which OXSTS has not")
        }
    }
}

private object XstsValueEvaluator : XstsExpressionVisitor<XstsValue>() {

    fun evaluate(expression: XstsExpression): XstsValue {
        return visit(expression)
    }

    private fun integer(expression: XstsExpression): BigInteger {
        val value = visit(expression)
        check(value is IntegerValue) {
            "Expected an integer in Theta's counterexample, got $value"
        }
        return value.value
    }

    private fun boolean(expression: XstsExpression): Boolean {
        val value = visit(expression)
        check(value is BooleanValue) {
            "Expected a boolean in Theta's counterexample, got $value"
        }
        return value.value
    }

    private fun array(expression: XstsExpression): ArrayValue {
        val value = visit(expression)
        check(value is ArrayValue) {
            "Expected an array in Theta's counterexample, got $value"
        }
        return value
    }

    // Theta's integer division is Euclidean, mod is never negative and rem takes the sign of the divisor
    override fun visit(expression: ArithmeticBinaryOperator): XstsValue {
        val left = integer(expression.left)
        val right = integer(expression.right)
        val value = when (expression.op) {
            ArithmeticOp.ADD -> left + right
            ArithmeticOp.SUB -> left - right
            ArithmeticOp.MUL -> left * right
            ArithmeticOp.DIV -> (left - left.mod(right.abs())) / right
            ArithmeticOp.MOD -> left.mod(right.abs())
            ArithmeticOp.REM -> {
                if (right.signum() < 0) {
                    -left.mod(right.abs())
                } else {
                    left.mod(right.abs())
                }
            }
        }
        return IntegerValue(value)
    }

    override fun visit(expression: ArithmeticUnaryOperator): XstsValue {
        val body = integer(expression.body)
        return when (expression.op) {
            UnaryOp.PLUS -> IntegerValue(body)
            UnaryOp.MINUS -> IntegerValue(-body)
        }
    }

    override fun visit(expression: BooleanOperator): XstsValue {
        val left = boolean(expression.left)
        val right = boolean(expression.right)
        val value = when (expression.op) {
            BooleanOp.AND -> left && right
            BooleanOp.OR -> left || right
            BooleanOp.XOR -> left != right
            BooleanOp.IFF -> left == right
            BooleanOp.IMPLY -> !left || right
        }
        return BooleanValue(value)
    }

    override fun visit(expression: ComparisonOperator): XstsValue {
        val value = when (expression.op) {
            ComparisonOp.EQ -> visit(expression.left) == visit(expression.right)
            ComparisonOp.NOT_EQ -> visit(expression.left) != visit(expression.right)
            ComparisonOp.LESS -> integer(expression.left) < integer(expression.right)
            ComparisonOp.LESS_EQ -> integer(expression.left) <= integer(expression.right)
            ComparisonOp.GREATER -> integer(expression.left) > integer(expression.right)
            ComparisonOp.GREATER_EQ -> integer(expression.left) >= integer(expression.right)
        }
        return BooleanValue(value)
    }

    override fun visit(expression: ElementReferenceExpression): XstsValue {
        val element = expression.element
        check(element is EnumLiteral) {
            "A value in Theta's counterexample refers to $element, not to an enum literal"
        }
        return EnumValue(element)
    }

    override fun visit(expression: IfThenElseExpression): XstsValue {
        return if (boolean(expression.guard)) {
            visit(expression.then)
        } else {
            visit(expression.`else`)
        }
    }

    override fun visit(expression: NegationOperator): XstsValue {
        return BooleanValue(!boolean(expression.body))
    }

    override fun visit(expression: ReadIndexingSuffixExpression): XstsValue {
        return read(array(expression.primary), integer(expression.index))
    }

    // the grammar parses a read a[i] as a write without a value
    override fun visit(expression: WriteIndexingSuffixExpression): XstsValue {
        val array = array(expression.primary)
        val index = integer(expression.index)
        val written = expression.expression ?: return read(array, index)
        return array.copy(elements = array.elements + (index to visit(written)))
    }

    private fun read(array: ArrayValue, index: BigInteger): XstsValue {
        return array.elements[index] ?: array.default
    }

    override fun visit(expression: LiteralBoolean): XstsValue {
        return BooleanValue(expression.isValue)
    }

    override fun visit(expression: LiteralInteger): XstsValue {
        return IntegerValue(expression.value.toBigInteger())
    }

    override fun visit(expression: ConcreteLiteralArray): XstsValue {
        checkIntegerIndexed(expression.indexType)
        val elements = expression.values.associate {
            integer(it.index) to visit(it.value)
        }
        return ArrayValue(elements, visit(expression.elseExpression))
    }

    override fun visit(expression: DefaultLiteralArray): XstsValue {
        checkIntegerIndexed(expression.indexType)
        return ArrayValue(emptyMap(), visit(expression.elseExpression))
    }

    private fun checkIntegerIndexed(indexType: Type?) {
        check(indexType == null || indexType is IntegerType) {
            "Theta's counterexample holds an array indexed by $indexType, OXSTS arrays are indexed by integers"
        }
    }
}
