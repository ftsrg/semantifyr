package hu.bme.mit.gamma.oxsts.engine.transformation

import hu.bme.mit.gamma.oxsts.model.oxsts.AndOperator
import hu.bme.mit.gamma.oxsts.model.oxsts.AssumptionOperation
import hu.bme.mit.gamma.oxsts.model.oxsts.ChainReferenceExpression
import hu.bme.mit.gamma.oxsts.model.oxsts.ChainingExpression
import hu.bme.mit.gamma.oxsts.model.oxsts.DeclarationReferenceExpression
import hu.bme.mit.gamma.oxsts.model.oxsts.Element
import hu.bme.mit.gamma.oxsts.model.oxsts.Expression
import hu.bme.mit.gamma.oxsts.model.oxsts.InlineCall
import hu.bme.mit.gamma.oxsts.model.oxsts.LiteralBoolean
import hu.bme.mit.gamma.oxsts.model.oxsts.NotOperator
import hu.bme.mit.gamma.oxsts.model.oxsts.Operation
import hu.bme.mit.gamma.oxsts.model.oxsts.OrOperator
import hu.bme.mit.gamma.oxsts.model.oxsts.ReferenceExpression
import hu.bme.mit.gamma.oxsts.model.oxsts.impl.OxstsFactoryImpl
import org.eclipse.emf.ecore.EObject
import org.eclipse.xtext.EcoreUtil2

object OxstsFactory : OxstsFactoryImpl() {
    fun createEmptyOperation(): Operation {
        return createAssumptionOperation(createLiteralBoolean(true))
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

fun ReferenceExpression.asChainReferenceExpression(): ChainReferenceExpression {
    require(this is ChainReferenceExpression) {
        "No other kinds of expressions should be in the model at this point."
    }

    return this
}

fun ChainReferenceExpression.drop(n: Int): ChainReferenceExpression {
    return OxstsFactory.createChainReferenceExpression().also {
        it.chains += chains.copy().drop(n)
    }
}

fun ChainReferenceExpression.dropLast(n: Int): ChainReferenceExpression {
    return OxstsFactory.createChainReferenceExpression().also {
        it.chains += chains.copy().dropLast(n)
    }
}

fun ChainReferenceExpression.last(): ChainReferenceExpression {
    return OxstsFactory.createChainReferenceExpression().also {
        it.chains += chains.last()
    }
}

fun ChainReferenceExpression.appendWith(chainReferenceExpression: ChainReferenceExpression): ChainReferenceExpression {
    return OxstsFactory.createChainReferenceExpression().also {
        it.chains += chains.copy()
        it.chains += chainReferenceExpression.chains.copy()
    }
}

val ChainingExpression.element
    get() = (this as? DeclarationReferenceExpression)?.element

fun <T : EObject> T.copy() = EcoreUtil2.copy(this)
fun <T : EObject> Collection<T>.copy() = map { it.copy() }
