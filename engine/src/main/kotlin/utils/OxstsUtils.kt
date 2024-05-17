package hu.bme.mit.gamma.oxsts.engine.utils

import hu.bme.mit.gamma.oxsts.engine.transformation.OxstsFactory
import hu.bme.mit.gamma.oxsts.model.oxsts.BooleanType
import hu.bme.mit.gamma.oxsts.model.oxsts.ChainReferenceExpression
import hu.bme.mit.gamma.oxsts.model.oxsts.ChainingExpression
import hu.bme.mit.gamma.oxsts.model.oxsts.Containment
import hu.bme.mit.gamma.oxsts.model.oxsts.DeclarationReferenceExpression
import hu.bme.mit.gamma.oxsts.model.oxsts.Element
import hu.bme.mit.gamma.oxsts.model.oxsts.EnumLiteral
import hu.bme.mit.gamma.oxsts.model.oxsts.Feature
import hu.bme.mit.gamma.oxsts.model.oxsts.IntegerType
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
        it.chains += chains.drop(n).copy()
    }
}

fun ChainReferenceExpression.dropLast(n: Int): ChainReferenceExpression {
    return OxstsFactory.createChainReferenceExpression().also {
        it.chains += chains.dropLast(n).copy()
    }
}

fun ChainReferenceExpression.onlyFirst(): ChainReferenceExpression {
    return OxstsFactory.createChainReferenceExpression().also {
        it.chains += chains.first().copy()
    }
}

fun ChainReferenceExpression.onlyLast(): ChainReferenceExpression {
    return OxstsFactory.createChainReferenceExpression().also {
        it.chains += chains.last().copy()
    }
}

fun ChainReferenceExpression.lastChain(): ChainingExpression {
    return chains.last()
}

fun ChainReferenceExpression.appendWith(chainReferenceExpression: ChainReferenceExpression): ChainReferenceExpression {
    return OxstsFactory.createChainReferenceExpression().also {
        it.chains += chains.copy() + chainReferenceExpression.chains.copy()
    }
}

val ChainReferenceExpression.isStaticReference
    get() = referencedElementOrNull()?.isStatic ?: false

val Element.isStatic
    get() = when (this) {
        is EnumLiteral -> true
        else -> false
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

inline fun <reified T : Element> ReferenceExpression.typedReferencedElement(): T {
    val element = referencedElement()

    check(element is T) {
        "Reference $this must point to element of type ${T::class.qualifiedName}"
    }

    return element
}

fun ReferenceExpression.referencedElement(): Element {
    return referencedElementOrNull() ?: error("Expression $this must be DeclarationReferenceExpression")
}

fun ReferenceExpression.referencedElementOrNull(): Element? {
    require(this is ChainReferenceExpression)

    return chains.lastOrNull()?.referencedElementOrNull()
}


fun ChainingExpression.referencedElement(): Element {
    return referencedElementOrNull() ?: error("Expression $this must be DeclarationReferenceExpression")
}

fun ChainingExpression.referencedElementOrNull(): Element? {
    if (this is DeclarationReferenceExpression) {
        return element
    }

    return null
}

// TODO create caching containment and variable query

val Feature.allContainments: List<Containment>
    get() = allFeatures.filterIsInstance<Containment>()

val Feature.allFeatures: List<Feature>
    get() = type.allFeatures

val Feature.allVariables: List<Variable>
    get() = type.allVariables

val Type.allFeatures: List<Feature>
    get() {
        val list = mutableListOf<Feature>()
        list += features
        if (supertype != null) {
            // FIXME: recursive property accessor
            list += supertype.allFeatures
        }
        return list
    }

val Type.allVariables: List<Variable>
    get() {
        val list = mutableListOf<Variable>()
        list += variables
        if (supertype != null) {
            // FIXME: recursive property accessor
            list += supertype.allVariables
        }
        return list
    }

val Feature.isDataType
    get() = typing is IntegerType || typing is BooleanType


val Feature.allSubsets: Set<Feature>
    get() {
        val features = subsets?.toMutableSet() ?: mutableSetOf()

        if (redefines != null) {
            // FIXME: recursive property accessor
            features += redefines
            features += redefines.allSubsets
        }

        return features
    }
