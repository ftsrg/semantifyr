/*
 * SPDX-FileCopyrightText: 2023-2024 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.semantifyr.utils

import hu.bme.mit.semantifyr.oxsts.model.oxsts.BaseType
import hu.bme.mit.semantifyr.oxsts.model.oxsts.BooleanType
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ChainReferenceExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ChainingExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Containment
import hu.bme.mit.semantifyr.oxsts.model.oxsts.DataType
import hu.bme.mit.semantifyr.oxsts.model.oxsts.DeclarationReferenceExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Element
import hu.bme.mit.semantifyr.oxsts.model.oxsts.EnumLiteral
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Feature
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Instance
import hu.bme.mit.semantifyr.oxsts.model.oxsts.IntegerType
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Package
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Pattern
import hu.bme.mit.semantifyr.oxsts.model.oxsts.PatternConstraint
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Property
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ReferenceExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ReferenceTyping
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Transition
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Type
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Variable
import org.eclipse.xtext.EcoreUtil2
import java.util.*
import kotlin.collections.ArrayDeque

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

val Variable.isDataTyped
    get() = typing is DataType

val ReferenceTyping.referencedElement
    get() = reference.chains.last().element

val ChainingExpression.element
    get() = (this as? DeclarationReferenceExpression)?.element

fun Instance.createReference(): List<ChainingExpression> {
    val context = ArrayDeque<ChainingExpression>()
    var current: Instance? = this

    while (current != null) {
        context.addFirst(OxstsFactory.createDeclarationReference(current.containment))
        current = current.parent
    }

    context.removeFirst() // remove rootInstance reference

    return context
}

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

fun Type.findInitTransition(): Transition {
    return initTransition.singleOrNull() ?: supertype?.findInitTransition() ?: error("No init transition found!")
}

fun Type.findMainTransition(): Transition {
    return mainTransition.singleOrNull() ?: supertype?.findMainTransition() ?: error("No main transition found!")
}

fun Type.findProperty(): Property {
    return properties.singleOrNull() ?: supertype?.findProperty() ?: error("No property in type hierarchy!")
}

val Transition.baseType: BaseType
    get() = EcoreUtil2.getContainerOfType(this, BaseType::class.java)

val Transition.isMainTransition: Boolean
    get() = baseType.mainTransition.contains(this)

val Transition.isInitTransition: Boolean
    get() = baseType.initTransition.contains(this)

val Transition.isHavocTransition: Boolean
    get() = baseType.havocTransition.contains(this)

val Pattern.fullyQualifiedName
    get() = "${(eContainer() as Package).name}__$name"

fun Pattern.allReferencedPatterns(): Set<Pattern> {
    val patterns = mutableSetOf<Pattern>()

    val patternQueue = LinkedList<Pattern>()

    patternQueue += this

    while (patternQueue.any()) {
        val referencedPattern = patternQueue.removeFirst()
        if (patterns.add(referencedPattern)) {
            val patternConstraints = EcoreUtil2.getAllContentsOfType(referencedPattern, PatternConstraint::class.java)
            patternQueue += patternConstraints.map {
                it.pattern
            }
        }
    }

    return patterns
}
