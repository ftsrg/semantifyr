/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.nuxmv.transformation

import com.google.inject.assistedinject.Assisted
import com.google.inject.assistedinject.AssistedInject
import hu.bme.mit.semantifyr.backend.BackendUnsupportedException
import hu.bme.mit.semantifyr.backend.transformation.BackendExpressionVisitor
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ArithmeticBinaryOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ArithmeticOp
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ArithmeticUnaryOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.BooleanOp
import hu.bme.mit.semantifyr.oxsts.model.oxsts.BooleanOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ComparisonOp
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ComparisonOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ElementReference
import hu.bme.mit.semantifyr.oxsts.model.oxsts.EnumLiteral
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.IfThenElse
import hu.bme.mit.semantifyr.oxsts.model.oxsts.IndexingSuffixExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralBoolean
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralInteger
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralReal
import hu.bme.mit.semantifyr.oxsts.model.oxsts.NegationOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.UnaryOp
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration

class NuxmvExpressionVisitor @AssistedInject constructor(
    @param:Assisted private val substitution: Map<VariableDeclaration, String>,
    private val nuxmvVariableTransformer: NuxmvVariableTransformer,
) : BackendExpressionVisitor<String>() {

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
            ComparisonOp.EQ -> "="
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
            BooleanOp.AND -> "&"
            BooleanOp.OR -> "|"
            BooleanOp.XOR -> "xor"
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
        return if (expression.isValue) {
            "TRUE"
        } else {
            "FALSE"
        }
    }

    override fun visit(expression: LiteralReal): String {
        return expression.value.toString()
    }

    override fun visit(expression: ElementReference): String {
        return when (val element = expression.element) {
            is VariableDeclaration -> substitution[element] ?: nuxmvVariableTransformer.nameOf(element)
            is EnumLiteral -> element.name
            else -> error("Unexpected kind of element: $element")
        }
    }

    override fun visit(expression: IndexingSuffixExpression): String {
        throw BackendUnsupportedException("nuXmv does not support array indexing")
    }

    override fun visit(expression: IfThenElse): String {
        return "case ${visit(expression.guard)} : ${visit(expression.then)}; TRUE : ${visit(expression.`else`)}; esac"
    }

    interface Factory {
        fun create(substitution: Map<VariableDeclaration, String>): NuxmvExpressionVisitor
    }
}
