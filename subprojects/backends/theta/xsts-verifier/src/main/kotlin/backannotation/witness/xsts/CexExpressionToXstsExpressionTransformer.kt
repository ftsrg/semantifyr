/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.theta.verification.backannotation.witness.xsts

import hu.bme.mit.semantifyr.cex.lang.cex.ArithmeticUnaryOperator
import hu.bme.mit.semantifyr.cex.lang.cex.LiteralBoolean
import hu.bme.mit.semantifyr.cex.lang.cex.LiteralEnum
import hu.bme.mit.semantifyr.cex.lang.cex.LiteralInteger
import hu.bme.mit.semantifyr.cex.lang.cex.UnaryOp
import hu.bme.mit.semantifyr.cex.lang.utils.CexExpressionVisitor
import hu.bme.mit.semantifyr.xsts.lang.xsts.Expression
import hu.bme.mit.semantifyr.xsts.lang.xsts.XstsFactory
import hu.bme.mit.semantifyr.xsts.lang.xsts.XstsModel

class CexExpressionToXstsExpressionTransformer(
    val xstsModel: XstsModel
) : CexExpressionVisitor<Expression>() {

    fun transform(expression: hu.bme.mit.semantifyr.cex.lang.cex.Expression): Expression {
        return visit(expression)
    }

    private fun transform(op: UnaryOp): hu.bme.mit.semantifyr.xsts.lang.xsts.UnaryOp {
        return when (op) {
            UnaryOp.PLUS -> hu.bme.mit.semantifyr.xsts.lang.xsts.UnaryOp.PLUS
            UnaryOp.MINUS -> hu.bme.mit.semantifyr.xsts.lang.xsts.UnaryOp.MINUS
        }
    }

    override fun visit(expression: ArithmeticUnaryOperator): Expression {
        return XstsFactory.eINSTANCE.createArithmeticUnaryOperator().also {
            it.body = visit(expression.body)
            it.op = transform(expression.op)
        }
    }

    override fun visit(expression: LiteralInteger): Expression {
        return XstsFactory.eINSTANCE.createLiteralInteger().also {
            it.value = expression.value
        }
    }

    override fun visit(expression: LiteralBoolean): Expression {
        return XstsFactory.eINSTANCE.createLiteralBoolean().also {
            it.isValue = expression.isValue
        }
    }

    override fun visit(expression: LiteralEnum): Expression {
        val enumDeclaration = xstsModel.enumDeclarations.firstOrNull {
            it.name == expression.type
        }
        check(enumDeclaration != null) {
            "No EnumDeclaration found with name ${expression.type}"
        }
        val enumLiteral = enumDeclaration.literals.firstOrNull {
            it.name == expression.value
        }
        check(enumLiteral != null) {
            "No EnumLiteral found with name ${expression.type}.${expression.value}"
        }

        return XstsFactory.eINSTANCE.createElementReferenceExpression().also {
            it.element = enumLiteral
        }
    }

}
