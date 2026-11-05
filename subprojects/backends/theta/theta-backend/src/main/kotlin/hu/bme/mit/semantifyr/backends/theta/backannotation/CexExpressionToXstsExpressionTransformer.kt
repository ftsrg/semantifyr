/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.theta.backannotation

import hu.bme.mit.semantifyr.cex.lang.cex.ArithmeticUnaryOperator
import hu.bme.mit.semantifyr.cex.lang.cex.LiteralBoolean
import hu.bme.mit.semantifyr.cex.lang.cex.LiteralEnum
import hu.bme.mit.semantifyr.cex.lang.cex.LiteralInteger
import hu.bme.mit.semantifyr.cex.lang.cex.UnaryOp
import hu.bme.mit.semantifyr.cex.lang.utils.CexExpressionVisitor
import hu.bme.mit.semantifyr.xsts.lang.xsts.Expression
import hu.bme.mit.semantifyr.xsts.lang.xsts.XstsFactory
import hu.bme.mit.semantifyr.xsts.lang.xsts.XstsModel
import java.math.BigInteger

private typealias CexExpression = hu.bme.mit.semantifyr.cex.lang.cex.Expression

class CexExpressionToXstsExpressionTransformer(
    val xstsModel: XstsModel,
) : CexExpressionVisitor<Expression>() {

    fun transform(expression: CexExpression): Expression {
        return visit(expression)
    }

    override fun visit(expression: ArithmeticUnaryOperator): Expression {
        return integerLiteral(integerValue(expression))
    }

    override fun visit(expression: LiteralInteger): Expression {
        return integerLiteral(expression.value)
    }

    private fun integerValue(expression: CexExpression): BigInteger {
        return when (expression) {
            is LiteralInteger -> expression.value
            is ArithmeticUnaryOperator -> {
                when (expression.op) {
                    UnaryOp.PLUS -> integerValue(expression.body)
                    UnaryOp.MINUS -> integerValue(expression.body).negate()
                }
            }
            else -> error("Unexpected operand of a sign in a counterexample: $expression")
        }
    }

    private fun integerLiteral(value: BigInteger): Expression {
        if (!value.fitsInt()) {
            throw UnrepresentableWitnessValueException("Theta's counterexample holds $value, beyond the 32-bit integers of OXSTS")
        }
        return XstsFactory.eINSTANCE.createLiteralInteger().also {
            it.value = value.toInt()
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
