/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.gamma.serialization

import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.ArithmeticUnaryOperator
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.BooleanType
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.CascadeComponentDeclaration
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.EventDeclaration
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.EventDirection
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.EventParameterReferenceExpression
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.FiredTransitionExpression
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.GammaModelPackage
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.InitialState
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.InstanceElementExpression
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.IntegerType
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.InterfaceDeclaration
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.LiteralExpression
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.Persistency
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.RaiseEventAct
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.RaiseEventAction
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.RecordAccessExpression
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.Region
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.ShallowHistoryState
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.StateNode
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.StatechartDeclaration
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.Transition
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.TypeDeclaration
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.VariableDeclaration
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.VerificationCaseDeclaration
import org.eclipse.emf.ecore.EObject
import org.eclipse.xtext.EcoreUtil2
import org.eclipse.xtext.nodemodel.util.NodeModelUtils

class UnmappedGammaConstruct(
    val element: EObject,
    val construct: String,
) {
    val location: String
        get() {
            val file = element.eResource()?.uri?.lastSegment() ?: "<unknown>"
            val line = NodeModelUtils.getNode(element)?.startLine ?: 0
            return "$file:$line"
        }

    override fun toString(): String {
        return "$location: $construct is not mapped to OXSTS yet"
    }
}

class UnmappedGammaConstructException(
    val constructs: List<UnmappedGammaConstruct>,
) : RuntimeException(constructs.joinToString("\n"))

object UnmappedGammaConstructs {

    fun check(gammaPackage: GammaModelPackage) {
        val constructs = collect(gammaPackage)
        if (constructs.isNotEmpty()) {
            throw UnmappedGammaConstructException(constructs)
        }
    }

    fun collect(gammaPackage: GammaModelPackage): List<UnmappedGammaConstruct> {
        val constructs = mutableListOf<UnmappedGammaConstruct>()
        for (element in EcoreUtil2.eAllContentsAsList(gammaPackage)) {
            describe(element)?.let {
                constructs += UnmappedGammaConstruct(element, it)
            }
        }
        return constructs
    }

    private fun describe(element: EObject): String? {
        return when (element) {
            is TypeDeclaration -> {
                "the type declaration '${element.name}'"
            }
            is InterfaceDeclaration -> {
                if (element.parents.isNotEmpty()) {
                    "the interface inheritance of '${element.name}'"
                } else {
                    null
                }
            }
            is EventDeclaration -> {
                describe(element)
            }
            is CascadeComponentDeclaration -> {
                "the cascade composite '${element.name}'"
            }
            is VariableDeclaration -> {
                describe(element)
            }
            is ShallowHistoryState -> {
                "the shallow history node '${element.name}'"
            }
            is Transition -> {
                describe(element)
            }
            is RaiseEventAction -> {
                if (element.arguments.isNotEmpty()) {
                    "a raise with event arguments"
                } else {
                    null
                }
            }
            is EventParameterReferenceExpression -> {
                "the event parameter reference '${element.parameter?.name}'"
            }
            is RecordAccessExpression -> {
                "the record field access '${element.field?.name}'"
            }
            is RaiseEventAct -> {
                "a raised event in a property, for which the library has no expression"
            }
            is FiredTransitionExpression -> {
                "a fired transition in a property"
            }
            is InstanceElementExpression -> {
                if (EcoreUtil2.getContainerOfType(element, VerificationCaseDeclaration::class.java) == null) {
                    "a component instance reference in a statechart"
                } else {
                    null
                }
            }
            else -> {
                null
            }
        }
    }

    private fun describe(event: EventDeclaration): String? {
        if (event.direction == EventDirection.INOUT) {
            return "the inout event '${event.name}'"
        }
        if (event.parameters.isNotEmpty()) {
            return "the parameters of event '${event.name}'"
        }
        if (event.persistency == Persistency.PERSISTENT) {
            return "the persistent event '${event.name}'"
        }
        return null
    }

    private fun describe(variable: VariableDeclaration): String? {
        if (variable.type !is IntegerType && variable.type !is BooleanType) {
            return "the type of variable '${variable.name}'"
        }
        if (variable.default != null && variable.default !is LiteralExpression && variable.default !is ArithmeticUnaryOperator) {
            return "the non-literal initial value of variable '${variable.name}'"
        }
        return null
    }

    private fun describe(transition: Transition): String? {
        if (transition.to is ShallowHistoryState) {
            return "a transition to the history node '${transition.to.name}'"
        }
        if (transition.from is InitialState) {
            if (transition.trigger != null || transition.guard != null || transition.actions.isNotEmpty()) {
                return "an initial transition with a trigger, guard or action"
            }
            if (transition.from.eContainer() != transition.to.eContainer()) {
                return "an initial transition to '${transition.to.name}' in another region"
            }
        }
        if (isBetweenOrthogonalRegions(transition)) {
            return "a transition from '${transition.from.name}' to '${transition.to.name}' between orthogonal regions"
        }
        return null
    }

    // Gamma's validator rejects a transition whose ends lie in orthogonal regions, where no lowest common region holds both
    private fun isBetweenOrthogonalRegions(transition: Transition): Boolean {
        val sourceAncestors = ancestors(transition.from)
        val targetAncestors = ancestors(transition.to)
        val commonRegion = targetAncestors.filterIsInstance<Region>().firstOrNull {
            it in sourceAncestors
        } ?: return true
        val sourceSide = sourceAncestors.getOrElse(sourceAncestors.indexOf(commonRegion) - 1) {
            transition.from
        }
        val targetSide = targetAncestors.getOrElse(targetAncestors.indexOf(commonRegion) - 1) {
            transition.to
        }
        return sourceSide == targetSide && sourceSide != transition.from && targetSide != transition.to
    }

    private fun ancestors(stateNode: StateNode): List<EObject> {
        val ancestors = mutableListOf<EObject>()
        var current = stateNode.eContainer()
        while (current !is StatechartDeclaration) {
            ancestors += current
            current = current.eContainer()
        }
        return ancestors
    }
}
