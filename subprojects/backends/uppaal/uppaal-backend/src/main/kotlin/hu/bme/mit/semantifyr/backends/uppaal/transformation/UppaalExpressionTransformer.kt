/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.uppaal.transformation

import com.google.inject.Inject
import hu.bme.mit.semantifyr.oxsts.lang.utils.ExpressionVisitor
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AG
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ArithmeticBinaryOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ArithmeticOp
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ArithmeticUnaryOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ArrayLiteral
import hu.bme.mit.semantifyr.oxsts.model.oxsts.BooleanOp
import hu.bme.mit.semantifyr.oxsts.model.oxsts.BooleanOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.CallSuffixExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.CastExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ComparisonOp
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ComparisonOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.EF
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ElementReference
import hu.bme.mit.semantifyr.oxsts.model.oxsts.EnumLiteral
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.IfThenElse
import hu.bme.mit.semantifyr.oxsts.model.oxsts.IndexingSuffixExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralBoolean
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralInfinity
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralInteger
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralNothing
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralReal
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralString
import hu.bme.mit.semantifyr.oxsts.model.oxsts.NavigationSuffixExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.NegationOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.RangeExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.SelfReference
import hu.bme.mit.semantifyr.oxsts.model.oxsts.UnaryOp
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration

class UppaalExpressionTransformer : ExpressionVisitor<String>() {
    @Inject
    private lateinit var uppaalVariableTransformer: UppaalVariableTransformer

    fun transform(expression: Expression): String {
        return visit(expression)
    }

    override fun visit(expression: ComparisonOperator): String {
        val left = visit(expression.left)
        val right = visit(expression.right)
        val op = when (expression.op) {
            ComparisonOp.LESS -> "<"
            ComparisonOp.LESS_EQ -> "<="
            ComparisonOp.GREATER -> ">"
            ComparisonOp.GREATER_EQ -> ">="
            ComparisonOp.EQ -> "=="
            ComparisonOp.NOT_EQ -> "!="
        }
        return "($left $op $right)"
    }

    override fun visit(expression: ArithmeticBinaryOperator): String {
        val left = visit(expression.left)
        val right = visit(expression.right)
        val op = when (expression.op) {
            ArithmeticOp.ADD -> "+"
            ArithmeticOp.SUB -> "-"
            ArithmeticOp.MUL -> "*"
            ArithmeticOp.DIV -> "/"
        }
        return "($left $op $right)"
    }

    override fun visit(expression: BooleanOperator): String {
        val left = visit(expression.left)
        val right = visit(expression.right)
        val op = when (expression.op) {
            BooleanOp.AND -> "&&"
            BooleanOp.OR -> "||"
            BooleanOp.XOR -> "!="
        }
        return "($left $op $right)"
    }

    override fun visit(expression: ArithmeticUnaryOperator): String {
        val body = visit(expression.body)
        val op = when (expression.op) {
            UnaryOp.PLUS -> "+"
            UnaryOp.MINUS -> "-"
        }
        return "($op$body)"
    }

    override fun visit(expression: NegationOperator): String {
        return "!(${visit(expression.body)})"
    }

    override fun visit(expression: LiteralInteger): String {
        return expression.value.toString()
    }

    override fun visit(expression: LiteralBoolean): String {
        return if (expression.isValue) "true" else "false"
    }

    override fun visit(expression: LiteralReal): String {
        return expression.value.toString()
    }

    override fun visit(expression: ElementReference): String {
        return when (val element = expression.element) {
            is VariableDeclaration -> uppaalVariableTransformer.nameOf(element)
            is EnumLiteral -> element.name
            else -> error("Unexpected kind of element: $element")
        }
    }

    override fun visit(expression: AG): String {
        error("AG should be handled at the property level, not inside expression bodies")
    }

    override fun visit(expression: EF): String {
        error("EF should be handled at the property level, not inside expression bodies")
    }

    override fun visit(expression: RangeExpression): String = error("Range expressions have no direct Uppaal equivalent")

    override fun visit(expression: ArrayLiteral): String = error("Array literals are not yet supported in the Uppaal backend")

    override fun visit(expression: LiteralInfinity): String = error("Infinity literals are not supported in Uppaal")

    override fun visit(expression: LiteralString): String = error("String literals are not supported in Uppaal")

    override fun visit(expression: LiteralNothing): String = error("Nothing literals are not supported in Uppaal")

    override fun visit(expression: SelfReference): String = error("Self references should have been resolved before Uppaal transformation")

    override fun visit(expression: NavigationSuffixExpression): String = error("Navigation expressions should have been resolved before Uppaal transformation")

    override fun visit(expression: CallSuffixExpression): String = error("Call expressions should have been resolved before Uppaal transformation")

    override fun visit(expression: IndexingSuffixExpression): String = error("Indexing expressions are not yet supported in the Uppaal backend")

    override fun visit(expression: CastExpression): String = visit(expression.body)

    override fun visit(expression: IfThenElse): String = "((${visit(expression.guard)}) ? (${visit(expression.then)}) : (${visit(expression.`else`)}))"
}
