package hu.bme.mit.gamma.oxsts.engine.transformation

import hu.bme.mit.gamma.oxsts.model.oxsts.AndOperator
import hu.bme.mit.gamma.oxsts.model.oxsts.AssumptionOperation
import hu.bme.mit.gamma.oxsts.model.oxsts.ChainReferenceExpression
import hu.bme.mit.gamma.oxsts.model.oxsts.ChainingExpression
import hu.bme.mit.gamma.oxsts.model.oxsts.DeclarationReferenceExpression
import hu.bme.mit.gamma.oxsts.model.oxsts.Element
import hu.bme.mit.gamma.oxsts.model.oxsts.EnumLiteral
import hu.bme.mit.gamma.oxsts.model.oxsts.Expression
import hu.bme.mit.gamma.oxsts.model.oxsts.InlineCall
import hu.bme.mit.gamma.oxsts.model.oxsts.LiteralBoolean
import hu.bme.mit.gamma.oxsts.model.oxsts.NotOperator
import hu.bme.mit.gamma.oxsts.model.oxsts.Operation
import hu.bme.mit.gamma.oxsts.model.oxsts.OrOperator
import hu.bme.mit.gamma.oxsts.model.oxsts.ReferenceExpression
import hu.bme.mit.gamma.oxsts.model.oxsts.ReferenceTyping
import hu.bme.mit.gamma.oxsts.model.oxsts.impl.OxstsFactoryImpl

object OxstsFactory : OxstsFactoryImpl() {
    fun createEmptyOperation(): Operation {
        return createAssumptionOperation(createLiteralBoolean(true))
    }

    fun createEnumLiteral(name: String): EnumLiteral {
        return createEnumLiteral().also {
            it.name = name
        }
    }

    fun createReferenceTyping(element: Element): ReferenceTyping {
        return createReferenceTyping().also {
            it.reference = createChainReferenceExpression(element)
        }
    }

    fun createDeclarationReferenceExpression(element: Element): DeclarationReferenceExpression {
        return createDeclarationReference(element) as DeclarationReferenceExpression
    }

    fun createDeclarationReference(element: Element): ChainingExpression {
        return createDeclarationReferenceExpression().apply {
            this.element = element
        }
    }

    fun createChainReferenceExpression(element: Element): ChainReferenceExpression {
        return createChainReferenceExpression().apply {
            chains += createDeclarationReference(element)
        }
    }

    fun createChainReferenceExpression(expression: ChainingExpression): ChainReferenceExpression {
        return createChainReferenceExpression().apply {
            chains += expression
        }
    }

    fun createInlineCall(referenceExpression: ReferenceExpression): InlineCall {
        return createInlineCall().also {
            it.reference = referenceExpression
        }
    }

    fun createAssumptionOperation(expression: Expression): AssumptionOperation {
        return createAssumptionOperation().also {
            it.expression = expression
        }
    }

    fun createLiteralBoolean(value: Boolean): LiteralBoolean {
        return createLiteralBoolean().also {
            it.isValue = value
        }
    }

    fun createAndOperator(lhs: Expression, rhs: Expression): AndOperator {
        return createAndOperator().also {
            it.operands += lhs
            it.operands += rhs
        }
    }

    fun createOrOperator(lhs: Expression, rhs: Expression): OrOperator {
        return createOrOperator().also {
            it.operands += lhs
            it.operands += rhs
        }
    }

    fun createNotOperator(expression: Expression): NotOperator {
        return createNotOperator().also {
            it.operands += expression
        }
    }

}
