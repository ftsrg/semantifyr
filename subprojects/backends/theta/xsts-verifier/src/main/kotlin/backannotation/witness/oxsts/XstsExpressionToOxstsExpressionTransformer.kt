/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.theta.verification.backannotation.witness.inlinedoxsts

import com.google.inject.Inject
import hu.bme.mit.semantifyr.backends.theta.verification.transformation.xsts.OxstsDomainTransformer
import hu.bme.mit.semantifyr.semantics.transformation.injection.scope.CompilationScoped
import hu.bme.mit.semantifyr.semantics.utils.OxstsFactory
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
import hu.bme.mit.semantifyr.xsts.lang.xsts.Expression
import hu.bme.mit.semantifyr.xsts.lang.xsts.IfThenElseExpression
import hu.bme.mit.semantifyr.xsts.lang.xsts.IntegerType
import hu.bme.mit.semantifyr.xsts.lang.xsts.LiteralBoolean
import hu.bme.mit.semantifyr.xsts.lang.xsts.LiteralInteger
import hu.bme.mit.semantifyr.xsts.lang.xsts.NegationOperator
import hu.bme.mit.semantifyr.xsts.lang.xsts.ReadIndexingSuffixExpression
import hu.bme.mit.semantifyr.xsts.lang.xsts.UnaryOp
import hu.bme.mit.semantifyr.xsts.lang.xsts.WriteIndexingSuffixExpression

@CompilationScoped
class XstsExpressionToOxstsExpressionTransformer : XstsExpressionVisitor<hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression>() {

    @Inject
    private lateinit var oxstsExpressionTransformer: OxstsDomainTransformer

    fun transform(expression: Expression): hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression {
        return visit(expression)
    }

    private fun transform(op: ComparisonOp): hu.bme.mit.semantifyr.oxsts.model.oxsts.ComparisonOp {
        return when (op) {
            ComparisonOp.LESS -> hu.bme.mit.semantifyr.oxsts.model.oxsts.ComparisonOp.LESS
            ComparisonOp.LESS_EQ -> hu.bme.mit.semantifyr.oxsts.model.oxsts.ComparisonOp.LESS_EQ
            ComparisonOp.GREATER -> hu.bme.mit.semantifyr.oxsts.model.oxsts.ComparisonOp.GREATER
            ComparisonOp.GREATER_EQ -> hu.bme.mit.semantifyr.oxsts.model.oxsts.ComparisonOp.GREATER_EQ
            ComparisonOp.EQ -> hu.bme.mit.semantifyr.oxsts.model.oxsts.ComparisonOp.EQ
            ComparisonOp.NOT_EQ -> hu.bme.mit.semantifyr.oxsts.model.oxsts.ComparisonOp.NOT_EQ
        }
    }

    private fun transform(op: BooleanOp): hu.bme.mit.semantifyr.oxsts.model.oxsts.BooleanOp {
        return when (op) {
            BooleanOp.AND -> hu.bme.mit.semantifyr.oxsts.model.oxsts.BooleanOp.AND
            BooleanOp.OR -> hu.bme.mit.semantifyr.oxsts.model.oxsts.BooleanOp.OR
            BooleanOp.XOR -> hu.bme.mit.semantifyr.oxsts.model.oxsts.BooleanOp.XOR
            BooleanOp.IFF -> error("Not yet supported")
            BooleanOp.IMPLY -> error("Not yet supported")
        }
    }

    private fun transform(op: UnaryOp): hu.bme.mit.semantifyr.oxsts.model.oxsts.UnaryOp {
        return when (op) {
            UnaryOp.PLUS -> hu.bme.mit.semantifyr.oxsts.model.oxsts.UnaryOp.PLUS
            UnaryOp.MINUS -> hu.bme.mit.semantifyr.oxsts.model.oxsts.UnaryOp.MINUS
        }
    }

    private fun transform(op: ArithmeticOp): hu.bme.mit.semantifyr.oxsts.model.oxsts.ArithmeticOp {
        return when (op) {
            ArithmeticOp.ADD -> hu.bme.mit.semantifyr.oxsts.model.oxsts.ArithmeticOp.ADD
            ArithmeticOp.SUB -> hu.bme.mit.semantifyr.oxsts.model.oxsts.ArithmeticOp.SUB
            ArithmeticOp.MUL -> hu.bme.mit.semantifyr.oxsts.model.oxsts.ArithmeticOp.MUL
            ArithmeticOp.DIV -> hu.bme.mit.semantifyr.oxsts.model.oxsts.ArithmeticOp.DIV
            ArithmeticOp.MOD -> error("Not yet supported")
            ArithmeticOp.REM -> error("Not yet supported")
        }
    }

    override fun visit(expression: ArithmeticBinaryOperator): hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression {
        return OxstsFactory.createArithmeticBinaryOperator().also {
            it.op = transform(expression.op)
            it.left = transform(expression.left)
            it.right = transform(expression.right)
        }
    }

    override fun visit(expression: ArithmeticUnaryOperator): hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression {
        return OxstsFactory.createArithmeticUnaryOperator().also {
            it.op = transform(expression.op)
            it.body = transform(expression.body)
        }
    }

    override fun visit(expression: BooleanOperator): hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression {
        return OxstsFactory.createBooleanOperator().also {
            it.op = transform(expression.op)
            it.left = transform(expression.left)
            it.right = transform(expression.right)
        }
    }

    override fun visit(expression: ComparisonOperator): hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression {
        return OxstsFactory.createComparisonOperator().also {
            it.op = transform(expression.op)
            it.left = transform(expression.left)
            it.right = transform(expression.right)
        }
    }

    override fun visit(expression: ElementReferenceExpression): hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression {
        val element = expression.element

        require(element is EnumLiteral) {
            "This class is supposed to transform Cex expressions to Oxsts, which must only refer to enum literals!"
        }

        val originalElement = oxstsExpressionTransformer.getOriginal(element)

        return OxstsFactory.createElementReference(originalElement)
    }

    override fun visit(expression: IfThenElseExpression): hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression {
        error("IfThenElse Expressions are not supported in OXSTS")
    }

    override fun visit(expression: NegationOperator): hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression {
        return OxstsFactory.createNegationOperator().also {
            it.body = transform(expression.body)
        }
    }

    override fun visit(expression: ReadIndexingSuffixExpression): hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression {
        return OxstsFactory.createIndexingSuffixExpression().also {
            it.primary = transform(expression.primary)
            it.index = transform(expression.index)
        }
    }

    override fun visit(expression: WriteIndexingSuffixExpression): hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression {
        error("Default literal arrays are not supported in OXSTS")
    }

    override fun visit(expression: LiteralBoolean): hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression {
        return OxstsFactory.createLiteralBoolean(expression.isValue)
    }

    override fun visit(expression: LiteralInteger): hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression {
        return OxstsFactory.createLiteralInteger(expression.value)
    }

    override fun visit(expression: ConcreteLiteralArray): hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression {
        require(expression.elseExpression == null) {
            "Else expressions are not allowed in Oxsts!"
        }
        require(expression.indexType is IntegerType) {
            "Currently only integer index types are supported in array literals!"
        }
        for (i in expression.values.indices) {
            val index = expression.values[i].index
            require(index is LiteralInteger && index.value == i) {
                "Array literal indexes must be tightly packed from 0 to the size of the array!"
            }
        }

        return OxstsFactory.createArrayLiteral().also {
            it.values += expression.values.map {
                transform(it.value)
            }
        }
    }

    override fun visit(expression: DefaultLiteralArray): hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression {
        error("DefaultLiteral arrays are not supported in OXSTS")
    }

}
