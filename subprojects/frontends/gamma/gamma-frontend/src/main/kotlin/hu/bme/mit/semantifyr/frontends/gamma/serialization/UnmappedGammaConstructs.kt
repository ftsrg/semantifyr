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
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.GuardEvaluation
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.ImportStatement
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.InitialState
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.InstanceElementExpression
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.IntegerType
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.InterfaceDeclaration
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.LiteralExpression
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.Persistency
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.RaiseEventAct
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.RaiseEventAction
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.RecordAccessExpression
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.SetTimeoutAction
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.ShallowHistoryState
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.StatechartDeclaration
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.TimeUnit
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.Transition
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.TransitionPriority
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
            is ImportStatement -> {
                "the import of another package"
            }
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
            is StatechartDeclaration -> {
                describe(element)
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
            is SetTimeoutAction -> {
                if (element.unit == TimeUnit.MILLISECOND) {
                    "a timeout set in milliseconds"
                } else {
                    null
                }
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
                "a raised event in a property"
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

    private fun describe(statechart: StatechartDeclaration): String? {
        if (statechart.transitionPriority != TransitionPriority.OFF) {
            return "the transition priority ${statechart.transitionPriority.name.lowercase().replace('_', '-')} of '${statechart.name}'"
        }
        if (statechart.guardEvaluation != GuardEvaluation.ON_THE_FLY) {
            return "the guard evaluation ${statechart.guardEvaluation.name.lowercase().replace('_', '-')} of '${statechart.name}'"
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
        if (transition.from?.eContainer() != transition.to?.eContainer()) {
            return "a transition from '${transition.from?.name}' to '${transition.to?.name}' across regions"
        }
        if (transition.from is InitialState && (transition.trigger != null || transition.guard != null || transition.actions.isNotEmpty())) {
            return "an initial transition with a trigger, guard or action"
        }
        return null
    }
}
