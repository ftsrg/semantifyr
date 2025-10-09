/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.theta.verification.transformation.xsts

import com.google.inject.Inject
import hu.bme.mit.semantifyr.oxsts.lang.utils.ExpressionVisitor
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ArithmeticBinaryOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ArithmeticOp
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ArithmeticUnaryOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ArrayLiteral
import hu.bme.mit.semantifyr.oxsts.model.oxsts.BooleanOp
import hu.bme.mit.semantifyr.oxsts.model.oxsts.BooleanOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.CallSuffixExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ComparisonOp
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ComparisonOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Element
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ElementReference
import hu.bme.mit.semantifyr.oxsts.model.oxsts.EnumLiteral
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
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

private typealias XstsElement = org.eclipse.emf.ecore.EObject
private typealias XstsComparisonOp = hu.bme.mit.semantifyr.xsts.lang.xsts.ComparisonOp
private typealias XstsBooleanOp = hu.bme.mit.semantifyr.xsts.lang.xsts.BooleanOp
private typealias XstsUnaryOp = hu.bme.mit.semantifyr.xsts.lang.xsts.UnaryOp
private typealias XstsArithmeticOp = hu.bme.mit.semantifyr.xsts.lang.xsts.ArithmeticOp
private typealias XstsExpression = hu.bme.mit.semantifyr.xsts.lang.xsts.Expression
private typealias XstsElementReferenceExpression = hu.bme.mit.semantifyr.xsts.lang.xsts.ElementReferenceExpression

class OxstsExpressionTransformer : ExpressionVisitor<XstsExpression>() {

    @Inject
    private lateinit var oxstsVariableTransformer: OxstsVariableTransformer

    @Inject
    private lateinit var oxstsDomainTransformer: OxstsDomainTransformer

    fun transform(expression: Expression): XstsExpression {
        return visit(expression)
    }

    fun transformReference(expression: Expression): XstsElementReferenceExpression {
        val expression = transform(expression)

        if (expression is XstsElementReferenceExpression) {
            return expression
        }

        error("Expression was not evaluated to a reference!")
    }

    private fun transform(op: ComparisonOp): XstsComparisonOp {
        return when (op) {
            ComparisonOp.LESS -> XstsComparisonOp.LESS
            ComparisonOp.LESS_EQ -> XstsComparisonOp.LESS_EQ
            ComparisonOp.GREATER -> XstsComparisonOp.GREATER
            ComparisonOp.GREATER_EQ -> XstsComparisonOp.GREATER_EQ
            ComparisonOp.EQ -> XstsComparisonOp.EQ
            ComparisonOp.NOT_EQ -> XstsComparisonOp.NOT_EQ
        }
    }

    private fun transform(op: BooleanOp): XstsBooleanOp {
        return when (op) {
            BooleanOp.AND -> XstsBooleanOp.AND
            BooleanOp.OR -> XstsBooleanOp.OR
            BooleanOp.XOR -> XstsBooleanOp.XOR
        }
    }

    private fun transform(op: UnaryOp): XstsUnaryOp {
        return when (op) {
            UnaryOp.PLUS -> XstsUnaryOp.PLUS
            UnaryOp.MINUS -> XstsUnaryOp.MINUS
        }
    }

    private fun transform(op: ArithmeticOp): XstsArithmeticOp {
        return when (op) {
            ArithmeticOp.ADD -> XstsArithmeticOp.ADD
            ArithmeticOp.SUB -> XstsArithmeticOp.SUB
            ArithmeticOp.MUL -> XstsArithmeticOp.MUL
            ArithmeticOp.DIV -> XstsArithmeticOp.DIV
        }
    }

    override fun visit(expression: RangeExpression): XstsExpression {
        error("No equivalent in XSTS!")
    }

    override fun visit(expression: ComparisonOperator): XstsExpression {
        return XstsFactory.createComparisonOperator().also {
            it.left = transform(expression.left)
            it.right = transform(expression.right)
            it.op = transform(expression.op)
        }
    }

    override fun visit(expression: ArithmeticBinaryOperator): XstsExpression {
        return XstsFactory.createArithmeticBinaryOperator().also {
            it.left = transform(expression.left)
            it.right = transform(expression.right)
            it.op = transform(expression.op)
        }
    }

    override fun visit(expression: BooleanOperator): XstsExpression {
        return XstsFactory.createBooleanOperator().also {
            it.left = transform(expression.left)
            it.right = transform(expression.right)
            it.op = transform(expression.op)
        }
    }

    override fun visit(expression: ArithmeticUnaryOperator): XstsExpression {
        return XstsFactory.createArithmeticUnaryOperator().also {
            it.body = transform(expression.body)
            it.op = transform(expression.op)
        }
    }

    override fun visit(expression: NegationOperator): XstsExpression {
        return XstsFactory.createNegationOperator().also {
            it.body = transform(expression.body)
        }
    }

    override fun visit(expression: ArrayLiteral): XstsExpression {
        error("No equivalent in XSTS!")
    }

    override fun visit(expression: LiteralInfinity): XstsExpression {
        error("No equivalent in XSTS!")
    }

    override fun visit(expression: LiteralReal): XstsExpression {
        error("No equivalent in XSTS!")
    }

    override fun visit(expression: LiteralInteger): XstsExpression {
        return XstsFactory.createLiteralInteger().also {
            it.value = expression.value
        }
    }

    override fun visit(expression: LiteralString): XstsExpression {
        error("No equivalent in XSTS!")
    }

    override fun visit(expression: LiteralBoolean): XstsExpression {
        return XstsFactory.createLiteralBoolean().also {
            it.isValue = expression.isValue
        }
    }

    override fun visit(expression: LiteralNothing): XstsExpression {
        error("No equivalent in XSTS!")
    }

    override fun visit(expression: ElementReference): XstsExpression {
        return XstsFactory.createElementReferenceExpression().also {
            it.element = demandTransformElement(expression.element)
        }
    }

    private fun demandTransformElement(element: Element): XstsElement {
        return when (element) {
            is VariableDeclaration -> oxstsVariableTransformer.transform(element)
            is EnumLiteral -> oxstsDomainTransformer.transform(element)
            else -> error("Unexpected kind of element: $element")
        }
    }

    override fun visit(expression: SelfReference): XstsExpression {
        error("No equivalent in XSTS!")
    }

    override fun visit(expression: NavigationSuffixExpression): XstsExpression {
        error("No equivalent in XSTS!")
    }

    override fun visit(expression: CallSuffixExpression): XstsExpression {
        error("No equivalent in XSTS!")
    }

    override fun visit(expression: IndexingSuffixExpression): XstsExpression {
        return XstsFactory.createReadIndexingSuffixExpression().also {
            it.primary = transformReference(expression.primary)
            it.index = transform(expression.index)
        }
    }

}
