package hu.bme.mit.gamma.oxsts.engine.utils

import hu.bme.mit.gamma.oxsts.engine.transformation.OxstsFactory
import hu.bme.mit.gamma.oxsts.model.oxsts.ChainReferenceExpression
import hu.bme.mit.gamma.oxsts.model.oxsts.ChainingExpression
import hu.bme.mit.gamma.oxsts.model.oxsts.DeclarationReferenceExpression
import hu.bme.mit.gamma.oxsts.model.oxsts.Element
import hu.bme.mit.gamma.oxsts.model.oxsts.Feature
import hu.bme.mit.gamma.oxsts.model.oxsts.ReferenceExpression
import hu.bme.mit.gamma.oxsts.model.oxsts.ReferenceTyping
import hu.bme.mit.gamma.oxsts.model.oxsts.Type
import hu.bme.mit.gamma.oxsts.model.oxsts.Variable

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

fun ChainReferenceExpression.first(): ChainReferenceExpression {
    return OxstsFactory.createChainReferenceExpression().also {
        it.chains += chains.first()
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

val Feature.type
    get() = (typing as ReferenceTyping).referencedElement as Type

val Variable.isFeatureTyped
    get() = (typing as? ReferenceTyping)?.referencedElement is Feature

val ReferenceTyping.referencedElement
    get() = reference.chains.last().element

val ChainingExpression.element
    get() = (this as? DeclarationReferenceExpression)?.element


val Feature.isRedefine
    get() = redefines != null

inline fun <reified T : Element> ReferenceExpression.typedEvaluateElement(): T {
    val element = evaluateElement()

    check(element is T) {
        "Reference $this must point to element of type ${T::class.qualifiedName}"
    }

    return element
}

fun ReferenceExpression.evaluateElement(): Element {
    require(this is ChainReferenceExpression)

    return chains.last().evaluateElement()
}

fun ChainingExpression.evaluateElement(): Element {
    require(this is DeclarationReferenceExpression) {
        "Expression $this must be DeclarationReferenceExpression"
    }

    return element
}


val Feature.allSubsets: Set<Feature>
    get() {
        val features = subsets?.toMutableSet() ?: mutableSetOf()

        if (redefines != null) {
            features += redefines.allSubsets
        }

        return features
    }
