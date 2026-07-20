/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.spin.transformation

import com.google.inject.Inject
import hu.bme.mit.semantifyr.backend.BackendUnsupportedException
import hu.bme.mit.semantifyr.backend.transformation.BackendExpressionVisitor
import hu.bme.mit.semantifyr.oxsts.lang.library.builtin.BuiltinSymbolResolver
import hu.bme.mit.semantifyr.oxsts.lang.semantics.typesystem.ExpressionTypeEvaluatorProvider
import hu.bme.mit.semantifyr.oxsts.lang.semantics.typesystem.ImmutableTypeEvaluation
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
import hu.bme.mit.semantifyr.oxsts.model.oxsts.TemporalOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.UnaryOp
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration
import org.eclipse.xtext.EcoreUtil2

class SpinExpressionTransformer @Inject constructor(
    private val spinVariableTransformer: SpinVariableTransformer,
    private val expressionTypeEvaluatorProvider: ExpressionTypeEvaluatorProvider,
    private val builtinSymbolResolver: BuiltinSymbolResolver,
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
        return if (expression.isValue) {
            "true"
        } else {
            "false"
        }
    }

    override fun visit(expression: LiteralReal): String {
        throw BackendUnsupportedException("Spin does not support real literals")
    }

    override fun visit(expression: ElementReference): String {
        return when (val element = expression.element) {
            is VariableDeclaration -> spinVariableTransformer.nameOf(element)
            is EnumLiteral -> spinVariableTransformer.sanitizeEnumLiteral(element)
            else -> error("Unexpected kind of element: $element")
        }
    }

    override fun visit(expression: IndexingSuffixExpression): String {
        throw BackendUnsupportedException("Spin does not support array indexing")
    }

    override fun visit(expression: IfThenElse): String {
        val guard = visit(expression.guard)
        val then = visit(expression.then)
        val orElse = visit(expression.`else`)
        if (!isInsideTemporal(expression)) {
            return "(($guard) -> ($then) : ($orElse))"
        }
        if (!isBooleanTyped(expression)) {
            throw BackendUnsupportedException("Spin does not support non-boolean if-then-else inside a property body")
        }
        return "((($guard) && ($then)) || ((!($guard)) && ($orElse)))"
    }

    private fun isInsideTemporal(expression: Expression): Boolean {
        return EcoreUtil2.getContainerOfType(expression, TemporalOperator::class.java) != null
    }

    private fun isBooleanTyped(expression: Expression): Boolean {
        val evaluation = expressionTypeEvaluatorProvider.evaluate(expression) as? ImmutableTypeEvaluation ?: return false
        return evaluation.domainDeclaration === builtinSymbolResolver.boolDatatype(expression)
    }
}
