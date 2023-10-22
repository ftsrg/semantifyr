package hu.bme.mit.gamma.oxsts.engine.utils

import hu.bme.mit.gamma.oxsts.engine.transformation.OxstsFactory
import hu.bme.mit.gamma.oxsts.model.oxsts.ChainReferenceExpression
import hu.bme.mit.gamma.oxsts.model.oxsts.ChainingExpression
import hu.bme.mit.gamma.oxsts.model.oxsts.DeclarationReferenceExpression
import hu.bme.mit.gamma.oxsts.model.oxsts.Feature
import hu.bme.mit.gamma.oxsts.model.oxsts.ReferenceExpression
import hu.bme.mit.gamma.oxsts.model.oxsts.Variable
import hu.bme.mit.gamma.oxsts.model.oxsts.VariableTypeReference

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

fun ChainReferenceExpression.lastChain(): ChainingExpression {
    return chains.last()
}

fun ChainReferenceExpression.appendWith(chainReferenceExpression: ChainReferenceExpression): ChainReferenceExpression {
    return OxstsFactory.createChainReferenceExpression().also {
        it.chains += chains.copy()
        it.chains += chainReferenceExpression.chains.copy()
    }
}

val Variable.isFeatureTyped
    get() = (typing as? VariableTypeReference)?.reference is Feature

val ChainingExpression.element
    get() = (this as? DeclarationReferenceExpression)?.element
