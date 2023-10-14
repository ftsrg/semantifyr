package hu.bme.mit.gamma.oxsts.engine.transformation

import hu.bme.mit.gamma.oxsts.model.oxsts.ChainReferenceExpression
import hu.bme.mit.gamma.oxsts.model.oxsts.ChainingExpression
import hu.bme.mit.gamma.oxsts.model.oxsts.Element
import hu.bme.mit.gamma.oxsts.model.oxsts.InlineCall
import hu.bme.mit.gamma.oxsts.model.oxsts.Operation
import hu.bme.mit.gamma.oxsts.model.oxsts.ReferenceExpression
import hu.bme.mit.gamma.oxsts.model.oxsts.impl.OxstsFactoryImpl
import org.eclipse.emf.ecore.EObject
import org.eclipse.xtext.EcoreUtil2

fun <T : EObject> T.copy() = EcoreUtil2.copy(this)

object OxstsFactory : OxstsFactoryImpl() {
    fun createEmptyOperation(): Operation {
        return createSequenceOperation()
    }

    fun createChainingExpression(element: Element): ChainingExpression {
        return createDeclarationReferenceExpression().apply {
            this.element = element
        }
    }

    fun createChainReferenceExpression(element: Element): ChainReferenceExpression {
        return createChainReferenceExpression().apply {
            chains.add(createChainingExpression(element))
        }
    }

    fun createInlineCall(referenceExpression: ReferenceExpression): InlineCall {
        return createInlineCall().also {
            it.reference = referenceExpression
        }
    }

}

fun ReferenceExpression.asChainReferenceExpression(): ChainReferenceExpression = when (this) {
    is ChainReferenceExpression -> this
    else -> error("")
}

fun ChainReferenceExpression.exceptLast(): ChainReferenceExpression {
    return copy().also {
        it.chains.removeLast()
    }
}

fun ChainReferenceExpression.appendWith(chainReferenceExpression: ChainReferenceExpression): ChainReferenceExpression {
    return OxstsFactory.createChainReferenceExpression().also {
        it.chains.addAll(chains.copy())
        it.chains.addAll(chainReferenceExpression.chains.copy())
    }
}

fun <T : EObject> List<T>.copy() = map { it.copy() }
