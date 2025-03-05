/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.sysmlv2.frontend.serialization

import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.Action
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.AndOperator
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.AssignmentAction
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.ChainingExpression
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.Channel
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.Component
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.ComponentInstance
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.ElementReferenceExpression
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.EntryTransition
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.EqualityOperator
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.Event
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.EventTrigger
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.Expression
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.GreaterThanOperator
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.GreaterThanOrEqualsOperator
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.Guard
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.InequalityOperator
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.InputEvent
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.LessThanOperator
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.LessThanOrEqualsOperator
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.LiteralBoolean
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.LiteralExpression
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.LiteralInteger
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.MinusOperator
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.NotOperator
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.OrOperator
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.OutputEvent
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.Package
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.PlusOperator
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.RaiseEventAction
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.ReachabilityExpression
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.ReferenceExpression
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.Region
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.SetTimeoutAction
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.State
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.StateReachabilityExpression
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.StateTransition
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.Statechart
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.SyncComponent
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.Timeout
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.TimeoutTrigger
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.Transition
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.Trigger
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.Variable
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.VerificationCase
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.IndentationAwareStringWriter
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.appendIndent
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.indent
import org.eclipse.emf.ecore.EObject
import kotlin.reflect.KProperty

object SysMLv2ToOxstsSerializer {

    fun serialize(gammaPackage: Package) = indent {
        appendLine("package ${gammaPackage.name}")
        appendLine()

        appendLine("import Expressions")
        appendLine("import Variables")
        appendLine("import Statecharts")
        appendLine("import Components")
        appendLine("import Triggers")
        appendLine("import Actions")
        appendLine("import Events")
        appendLine()

        for (component in gammaPackage.components) {
            serialize(component)
        }

        for (verificationCase in gammaPackage.verificationCases) {
            serialize(verificationCase)
        }
    }

    private fun IndentationAwareStringWriter.serialize(component: Component) = when (component) {
        is Statechart -> serialize(component)
        is SyncComponent -> serialize(component)
        else -> error("Unknown component type: $component")
    }

    private fun IndentationAwareStringWriter.serialize(statechart: Statechart) {
        appendIndent("type ${statechart.name} : Statechart") {
            for (timeout in statechart.timeouts) {
                serialize(timeout)
            }

            for (variable in statechart.variables) {
                serialize(variable)
            }

            for (event in statechart.events) {
                serialize(event)
            }

            for (region in statechart.regions) {
                serialize(region)
            }
        }
    }

    private fun IndentationAwareStringWriter.serialize(variable: Variable) {

        appendIndent("containment ${variable.name} :> variables : ${variable.type}Variable") {
            if (variable.default != null) {
                val default = when (variable.default) {
                    is LiteralInteger -> (variable.default as LiteralInteger).value.toString()
                    is LiteralBoolean -> (variable.default as LiteralBoolean).isValue.toString()
                    else -> ""
                }

                appendLine("reference ::> defaultValue : ${variable.type} = $default")
            }
        }
    }

    private fun IndentationAwareStringWriter.serialize(timeout: Timeout) {
        appendLine("containment ${timeout.name} :> timeouts : Timeout")
    }

    private fun IndentationAwareStringWriter.serialize(event: Event) = when (event) {
        is InputEvent -> serialize(event)
        is OutputEvent -> serialize(event)
        else -> error("Unknown event type: $event")
    }

    private fun IndentationAwareStringWriter.serialize(event: InputEvent) {
        appendLine("containment ${event.name} :> inputEvents : Event")
    }

    private fun IndentationAwareStringWriter.serialize(event: OutputEvent) {
        appendLine("containment ${event.name} :> outputEvents : Event")
    }

    private fun IndentationAwareStringWriter.serialize(region: Region) {
        appendIndent("containment ${region.name} :> regions : Region") {
            for (state in region.states) {
                serialize(state)
            }

            for (transition in region.transitions) {
                serialize(transition)
            }
        }
    }

    private fun IndentationAwareStringWriter.serialize(state: State) {
        appendIndent("containment ${state.name} :> states : State") {
            for (action in state.entryActions) {
                serialize(action, "entryActions")
            }

            for (action in state.exitActions) {
                serialize(action, "entryActions")
            }

            for (region in state.regions) {
                serialize(region)
            }
        }
    }

    private fun IndentationAwareStringWriter.serialize(transition: Transition) = when (transition) {
        is EntryTransition -> serialize(transition)
        is StateTransition -> serialize(transition)
        else -> error("Unknown State type $transition")
    }

    private fun IndentationAwareStringWriter.serialize(transition: EntryTransition) {
        appendIndent("containment ${transition.name} :> entryTransitions : EntryTransition") {
            appendLine("reference ::> to : State = ${transition.to.name}")
        }
    }

    private fun IndentationAwareStringWriter.serialize(transition: StateTransition) {
        appendIndent("containment ${transition.name} :> transitions : Transition") {
            appendLine("reference ::> from : State = ${transition.from.name}")
            appendLine("reference ::> to : State = ${transition.to.name}")

            if (transition.guard != null) {
                serialize(transition.guard)
            }

            if (transition.trigger != null) {
                serialize(transition.trigger)
            }

            for (action in transition.actions) {
                serialize(action, "actions")
            }
        }
    }

    private fun IndentationAwareStringWriter.serialize(guard: Guard) {
        appendIndent("containment ${guard.name} :> guards : Guard") {
            serialize(guard.expression, "expression")
        }
    }

    private fun IndentationAwareStringWriter.serialize(trigger: Trigger) = when (trigger) {
        is EventTrigger -> serialize(trigger)
        is TimeoutTrigger -> serialize(trigger)
        else -> error("Unknown trigger type: $trigger")
    }

    private fun IndentationAwareStringWriter.serialize(trigger: EventTrigger) {
        appendIndent("containment ${trigger.name} :> trigger : EventTrigger") {
            appendLine("reference ::> event : Event = ${trigger.event.name}")
        }
    }

    private fun IndentationAwareStringWriter.serialize(trigger: TimeoutTrigger) {
        appendIndent("containment ${trigger.name} :> trigger : TimeoutTrigger") {
            appendLine("reference ::> timeout : Timeout = ${trigger.timeout.name}")
        }
    }

    private fun IndentationAwareStringWriter.serialize(action: Action, subsets: String) = when (action) {
        is RaiseEventAction -> serialize(action, subsets)
        is SetTimeoutAction -> serialize(action, subsets)
        is AssignmentAction -> serialize(action, subsets)
        else -> error("Unknown action type: $action")
    }

    private fun IndentationAwareStringWriter.serialize(action: RaiseEventAction, subsets: String) {
        appendIndent("containment ${action.name} :> $subsets : RaiseEventAction") {
            appendLine("reference ::> event : Event = ${action.event.name}")
        }
    }

    private var a = 0
    private fun IndentationAwareStringWriter.serialize(action: SetTimeoutAction, subsets: String) {
        appendIndent("containment ${action.name} :> $subsets : SetTimeoutAction") {
            appendLine("reference ::> timeout : Timeout = ${action.timeout.name}")
            appendIndent("containment ee${a++} :> expression : LiteralIntegerExpression") {
                appendLine("reference ::> value : Integer = ${action.value}")
            }
        }
    }

    private fun IndentationAwareStringWriter.serialize(action: AssignmentAction, subsets: String) {
        appendIndent("containment ${action.name} :> $subsets : AssignmentAction") {
            appendLine("reference ::> variable : Variable = ${action.variable.name}")
            serialize(action.expression, "expression")
        }
    }

    private fun IndentationAwareStringWriter.serialize(component: SyncComponent) {
        appendIndent("type ${component.name} : SyncComponent") {
            for (event in component.events) {
                serialize(event)
            }

            for (instance in component.components) {
                serialize(instance)
            }

            for (channel in component.channels) {
                serialize(channel)
            }
        }
    }

    private fun IndentationAwareStringWriter.serialize(instance: ComponentInstance) {
        appendLine("containment ${instance.name} :> components : ${instance.component.name}")
    }

    private fun IndentationAwareStringWriter.serialize(channel: Channel) {
        appendIndent("containment ${channel.name} :> channels : Channel") {
            appendLine("reference ::> inputEvent : Event = ${channel.from.serialize()}")
            appendLine("reference ::> outputEvent : Event = ${channel.to.serialize()}")
        }
    }

    private fun IndentationAwareStringWriter.serialize(verificationCase: VerificationCase) {
        appendIndent("target ${verificationCase.name}") {
            appendLine("containment ${verificationCase.component.name} : ${verificationCase.component.component.name}")

            appendIndent("init") {
                appendLine("inline ${verificationCase.component.name}.init()")
            }

            appendIndent("tran") {
                appendLine("inline ${verificationCase.component.name}.havocInputEvents()")
                appendLine("inline ${verificationCase.component.name}.main()")
                appendLine("inline ${verificationCase.component.name}.passTime()")
            }

            appendLine("feature props : Expression[1..1]")

            serialize(verificationCase.expression, "props")

            appendIndent("prop") {
                appendLine("! (${verificationCase.expression.name}.evaluate)")
            }
        }
    }

    private fun IndentationAwareStringWriter.serialize(expression: Expression, subsets: String) = when (expression) {
        is ReferenceExpression -> serializeVariable(expression, subsets)
        is GreaterThanOperator -> serializeOperator(expression, expression.left, expression.right, "GreaterThanOperatorExpression", subsets)
        is GreaterThanOrEqualsOperator -> serializeOperator(expression, expression.left, expression.right, "GreaterThanOrEqualsOperatorExpression", subsets)
        is LessThanOperator -> serializeOperator(expression, expression.left, expression.right, "LessThanOperatorExpression", subsets)
        is LessThanOrEqualsOperator -> serializeOperator(expression, expression.left, expression.right, "LessThanOrEqualsOperatorExpression", subsets)
        is LiteralExpression -> serialize(expression, subsets)
        is MinusOperator -> serializeOperator(expression, expression.left, expression.right, "MinusOperatorExpression", subsets)
        is NotOperator -> serialize(expression, subsets)
        is OrOperator -> serializeOperator(expression, expression.left, expression.right, "OrOperatorExpression", subsets)
        is AndOperator -> serializeOperator(expression, expression.left, expression.right, "AndOperatorExpression", subsets)
        is PlusOperator -> serializeOperator(expression, expression.left, expression.right, "PlusOperatorExpression", subsets)
        is EqualityOperator -> serializeOperator(expression, expression.left, expression.right, "EqualityOperatorExpression", subsets)
        is InequalityOperator -> serializeOperator(expression, expression.left, expression.right, "InequalityOperatorExpression", subsets)
        is ReachabilityExpression -> serialize(expression, subsets)
        else -> error("Unknown type of expression: $expression")
    }

    private fun IndentationAwareStringWriter.serializeVariable(expression: ReferenceExpression, subsets: String) {
        appendIndent("containment ${expression.name} :> $subsets : VariableExpression") {
            appendLine("reference ::> variable : Variable = ${expression.serialize()}")
        }
    }

    private fun IndentationAwareStringWriter.serializeOperator(expression: Expression, left: Expression, right: Expression, type: String, subsets: String) {
        appendIndent("containment ${expression.name} :> $subsets : $type") {
            serialize(left, "left")
            serialize(right, "right")
        }
    }

    private fun IndentationAwareStringWriter.serialize(expression: NotOperator, subsets: String) {
        appendIndent("containment ${expression.name} :> $subsets : NotExpression") {
            serialize(expression.operand, "operand")
        }
    }

    private fun IndentationAwareStringWriter.serialize(expression: LiteralExpression, subsets: String) {
        when (expression) {
            is LiteralInteger -> appendIndent("containment ${expression.name} :> $subsets : LiteralIntegerExpression") {
                appendLine("reference ::> value : Integer = ${expression.value}")
            }
            is LiteralBoolean -> appendIndent("containment ${expression.name} :> $subsets : LiteralBooleanExpression") {
                appendLine("reference ::> value : Boolean = ${expression.isValue}")
            }
        }
    }

    private fun IndentationAwareStringWriter.serialize(expression: ReachabilityExpression, subsets: String) = when (expression) {
        is StateReachabilityExpression -> serialize(expression, subsets)
        else -> error("Unknown reachability expression: $expression")
    }

    private fun IndentationAwareStringWriter.serialize(expression: StateReachabilityExpression, subsets: String) {
        appendIndent("containment ${expression.name} :> $subsets : StateReachabilityExpression") {
            appendLine("reference ::> state : State = ${expression.expression.serialize()}")
        }
    }

    private fun ReferenceExpression.serialize() = when (this) {
        is ChainingExpression -> serialize()
        else -> error("Unknown reference expression: $this")
    }

    private fun ChainingExpression.serialize(): String {
        return elements.serialize()
    }

    private fun List<ElementReferenceExpression>.serialize(): String {
        return map {
            it.element
        }.joinToString(".") {
            it.name
        }
    }

}

open class EObjectNameProvider(
    private val prefix: String
) {
    private var number = 1
    private val nameMap = mutableMapOf<EObject, String>()

    fun getName(eObject: EObject) = nameMap.getOrPut(eObject) {
        prefix + number++
    }

    operator fun getValue(eObject: EObject, property: KProperty<*>) = getName(eObject)
}

object ActionNameProvider : EObjectNameProvider("action")
val Action.name by ActionNameProvider

object TransitionNameProvider : EObjectNameProvider("transition")
val Transition.name by TransitionNameProvider

object TriggerNameProvider : EObjectNameProvider("trigger")
val Trigger.name by TriggerNameProvider

object ChannelNameProvider : EObjectNameProvider("channel")
val Channel.name by ChannelNameProvider

object GuardNameProvider : EObjectNameProvider("guard")
val Guard.name by GuardNameProvider

object ExpressionNameProvider : EObjectNameProvider("expression")
val Expression.name by ExpressionNameProvider

val EObject.name
    get() = when (this) {
        is Action -> name
        is Transition -> name
        is Trigger -> name
        is Channel -> name
        is Guard -> name
        is Expression -> name
        else -> {
            val nameFeature = eClass().getEStructuralFeature("name")
            eGet(nameFeature).toString()
        }
    }
