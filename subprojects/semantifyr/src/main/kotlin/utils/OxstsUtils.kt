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
import org.eclipse.emf.ecore.EObject
import org.eclipse.xtext.EcoreUtil2
import java.util.*
import kotlin.collections.ArrayDeque

@Suppress("ObjectPropertyName")
val EObject._package: Package
    get() = if (this is Package) this else eContainer()._package

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
    return copy().also {
        it.chains += chainReferenceExpression.chains.copy()
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
    get() = (typing as ReferenceTyping).reference.typedReferencedElement<Type>()

val Variable.isFeatureTyped
    get() = (typing as? ReferenceTyping)?.reference?.referencedElementOrNull() is Feature

val Variable.isDataTyped
    get() = typing is DataType

val Feature.isRedefine
    get() = redefines != null

fun ReferenceTyping.referencedElement(): Element {
    return reference.referencedElement()
}

fun ReferenceTyping.referencedElementOrNull(): Element? {
    return reference.referencedElementOrNull()
}

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

val Feature.allContainments: Sequence<Containment>
    get() = allFeatures.filterIsInstance<Containment>()

val Feature.allFeatures: Sequence<Feature>
    get() = type.allFeatures

val Feature.allVariables: Sequence<Variable>
    get() = type.allVariables

val Type.allFeatures: Sequence<Feature>
    get() = sequence {
        yieldAll(features)
        if (supertype != null) {
            yieldAll(supertype.allFeatures)
        }
    }

val Type.allVariables: Sequence<Variable>
    get() = sequence {
        yieldAll(variables)
        if (supertype != null) {
            yieldAll(supertype.allVariables)
        }
    }

val Feature.isDataType
    get() = typing is IntegerType || typing is BooleanType

val Feature.allSubsets
    get() = internalAllSubsets.toSet()

private val Feature.internalAllSubsets: Sequence<Feature>
    get() = sequence {
        if (subsets != null) {
            yieldAll(subsets)
        }

        if (redefines != null) {
            yield(redefines)
            yieldAll(redefines.internalAllSubsets)
        }
    }

val BaseType.allLocalTransitions: Sequence<Transition>
    get() = transitions.asSequence() + initTransition.asSequence() + havocTransition.asSequence() + mainTransition.asSequence()

fun Type.findInitTransition(): Transition {
    return findLocalInitTransition() ?: supertype?.findInitTransition() ?: error("No init transition found!")
}

fun BaseType.findLocalInitTransition(): Transition? {
    return allLocalTransitions.firstOrNull {
        it.isInitTransition
    }
}

fun Type.findMainTransition(): Transition {
    return findLocalMainTransition() ?: supertype?.findMainTransition() ?: error("No main transition found!")
}

fun BaseType.findLocalMainTransition(): Transition? {
    return allLocalTransitions.firstOrNull {
        it.isMainTransition
    }
}


fun Type.findHavocTransition(): Transition {
    return findLocalHavocTransition() ?: supertype?.findHavocTransition() ?: error("No main transition found!")
}

fun BaseType.findLocalHavocTransition(): Transition? {
    return allLocalTransitions.firstOrNull {
        it.isHavocTransition
    }
}

fun Type.findProperty(): Property {
    return properties.firstOrNull() ?: supertype?.findProperty() ?: error("No property in type hierarchy!")
}

val Transition.baseType: BaseType
    get() = EcoreUtil2.getContainerOfType(this, BaseType::class.java)

val Transition.isMainTransition: Boolean
    get() = name == "main" || baseType.mainTransition.contains(this)

val Transition.isInitTransition: Boolean
    get() = name == "init" || baseType.initTransition.contains(this)

val Transition.isHavocTransition: Boolean
    get() = name == "havoc" || baseType.havocTransition.contains(this)

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
