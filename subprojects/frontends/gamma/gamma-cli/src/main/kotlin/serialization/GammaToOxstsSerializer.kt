/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.gamma.cli.serialization

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
import hu.bme.mit.semantifyr.semantics.utils.IndentationAwareStringWriter
import hu.bme.mit.semantifyr.semantics.utils.appendIndent
import hu.bme.mit.semantifyr.semantics.utils.indent
import org.eclipse.emf.ecore.EObject
import kotlin.reflect.KProperty

object GammaToOxstsSerializer {

    fun serialize(gammaPackage: Package) = indent {
        appendLine("package ${gammaPackage.name}")
        appendLine()


        appendLine("import semantifyr::verification")
        appendLine("import semantifyr::gamma::expressions")
        appendLine("import semantifyr::gamma::variables")
        appendLine("import semantifyr::gamma::statecharts")
        appendLine("import semantifyr::gamma::components")
        appendLine("import semantifyr::gamma::triggers")
        appendLine("import semantifyr::gamma::actions")
        appendLine("import semantifyr::gamma::events")
        appendLine("import semantifyr::gamma::verification")
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
        appendIndent("class ${statechart.name} : Statechart") {
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

        appendIndent("contains ${variable.name}: ${variable.type}Variable subsets variables") {
            if (variable.default != null) {
                val default = when (variable.default) {
                    is LiteralInteger -> (variable.default as LiteralInteger).value.toString()
                    is LiteralBoolean -> (variable.default as LiteralBoolean).isValue.toString()
                    else -> ""
                }

                appendLine("redefine refers defaultValue: ${transformVariableType(variable)} = $default")
            }
        }
    }

    private fun transformVariableType(variable: Variable): String {
        return when (variable.type) {
            "Integer" -> "int"
            "Boolean" -> "bool"
            else -> ""
        }
    }

    private fun IndentationAwareStringWriter.serialize(timeout: Timeout) {
        appendLine("contains ${timeout.name}: Timeout subsets timeouts")
    }

    private fun IndentationAwareStringWriter.serialize(event: Event) = when (event) {
        is InputEvent -> serialize(event)
        is OutputEvent -> serialize(event)
        else -> error("Unknown event type: $event")
    }

    private fun IndentationAwareStringWriter.serialize(event: InputEvent) {
        appendLine("contains ${event.name}: Event subsets inputEvents")
    }

    private fun IndentationAwareStringWriter.serialize(event: OutputEvent) {
        appendLine("contains ${event.name}: Event subsets outputEvents")
    }

    private fun IndentationAwareStringWriter.serialize(region: Region) {
        appendIndent("contains ${region.name}: Region subsets regions") {
            for (state in region.states) {
                serialize(state)
            }

            for (transition in region.transitions) {
                serialize(transition)
            }
        }
    }

    private fun IndentationAwareStringWriter.serialize(state: State) {
        appendIndent("contains ${state.name}: State subsets states") {
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
        appendIndent("contains ${transition.name}: EntryTransition subsets entryTransitions") {
            appendLine("redefine refers to: State = ${transition.to.name}")
        }
    }

    private fun IndentationAwareStringWriter.serialize(transition: StateTransition) {
        appendIndent("contains ${transition.name}: Transition subsets transitions") {
            appendLine("redefine refers from: State = ${transition.from.name}")
            appendLine("redefine refers to: State = ${transition.to.name}")

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
        appendIndent("redefine contains guard: Guard") {
            serialize(guard.expression, "expression")
        }
    }

    private fun IndentationAwareStringWriter.serialize(trigger: Trigger) = when (trigger) {
        is EventTrigger -> serialize(trigger)
        is TimeoutTrigger -> serialize(trigger)
        else -> error("Unknown trigger type: $trigger")
    }

    private fun IndentationAwareStringWriter.serialize(trigger: EventTrigger) {
        appendIndent("redefine contains trigger: EventTrigger") {
            appendLine("redefine refers event: Event = ${trigger.event.name}")
        }
    }

    private fun IndentationAwareStringWriter.serialize(trigger: TimeoutTrigger) {
        appendIndent("redefine contains trigger: TimeoutTrigger") {
            appendLine("redefine refers timeout: Timeout = ${trigger.timeout.name}")
        }
    }

    private fun IndentationAwareStringWriter.serialize(action: Action, subsets: String) = when (action) {
        is RaiseEventAction -> serialize(action, subsets)
        is SetTimeoutAction -> serialize(action, subsets)
        is AssignmentAction -> serialize(action, subsets)
        else -> error("Unknown action type: $action")
    }

    private fun IndentationAwareStringWriter.serialize(action: RaiseEventAction, subsets: String) {
        appendIndent("contains ${action.name}: RaiseEventAction subsets $subsets") {
            appendLine("redefine refers event: Event = ${action.event.name}")
        }
    }

    private var a = 0
    private fun IndentationAwareStringWriter.serialize(action: SetTimeoutAction, subsets: String) {
        appendIndent("contains ${action.name}: SetTimeoutAction subsets $subsets") {
            appendLine("redefine refers timeout: Timeout = ${action.timeout.name}")
            appendIndent("redefine contains expression: LiteralIntegerExpression") {
                appendLine("redefine refers value: int = ${action.value}")
            }
        }
    }

    private fun IndentationAwareStringWriter.serialize(action: AssignmentAction, subsets: String) {
        appendIndent("contains ${action.name}: AssignmentAction subsets $subsets") {
            appendLine("redefine refers variable: Variable = ${action.variable.name}")
            serialize(action.expression, "expression")
        }
    }

    private fun IndentationAwareStringWriter.serialize(component: SyncComponent) {
        appendIndent("class ${component.name} : SyncComponent") {
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
        appendLine("contains ${instance.name}: ${instance.component.name} subsets components")
    }

    private fun IndentationAwareStringWriter.serialize(channel: Channel) {
        appendIndent("contains ${channel.name}: Channel subsets channels") {
            appendLine("redefine refers inputEvent: Event = ${channel.from.serialize()}")
            appendLine("redefine refers outputEvent: Event = ${channel.to.serialize()}")
        }
    }

    private fun IndentationAwareStringWriter.serialize(verificationCase: VerificationCase) {
        appendLine("@VerificationCase(VerificationResult::UNSAFE)")
        appendIndent("class ${verificationCase.name} : GammaVerificationCase") {
            appendLine("contains ${verificationCase.component.name}: ${verificationCase.component.component.name} redefines component")

            serialize(verificationCase.expression, "propertyExpression")
        }
    }

    private fun IndentationAwareStringWriter.serialize(expression: Expression, redefines: String) = when (expression) {
        is ReferenceExpression -> serializeVariable(expression, redefines)
        is GreaterThanOperator -> serializeOperator(expression, expression.left, expression.right, "GreaterThanOperatorExpression", redefines)
        is GreaterThanOrEqualsOperator -> serializeOperator(expression, expression.left, expression.right, "GreaterThanOrEqualsOperatorExpression", redefines)
        is LessThanOperator -> serializeOperator(expression, expression.left, expression.right, "LessThanOperatorExpression", redefines)
        is LessThanOrEqualsOperator -> serializeOperator(expression, expression.left, expression.right, "LessThanOrEqualsOperatorExpression", redefines)
        is LiteralExpression -> serialize(expression, redefines)
        is MinusOperator -> serializeOperator(expression, expression.left, expression.right, "MinusOperatorExpression", redefines)
        is NotOperator -> serialize(expression, redefines)
        is OrOperator -> serializeOperator(expression, expression.left, expression.right, "OrOperatorExpression", redefines)
        is AndOperator -> serializeOperator(expression, expression.left, expression.right, "AndOperatorExpression", redefines)
        is PlusOperator -> serializeOperator(expression, expression.left, expression.right, "PlusOperatorExpression", redefines)
        is EqualityOperator -> serializeOperator(expression, expression.left, expression.right, "EqualityOperatorExpression", redefines)
        is InequalityOperator -> serializeOperator(expression, expression.left, expression.right, "InequalityOperatorExpression", redefines)
        is ReachabilityExpression -> serialize(expression, redefines)
        else -> error("Unknown type of expression: $expression")
    }

    private fun IndentationAwareStringWriter.serializeVariable(expression: ReferenceExpression, redefines: String) {
        appendIndent("redefine contains $redefines: VariableExpression") {
            appendLine("redefine refers variable: Variable = ${expression.serialize()}")
        }
    }

    private fun IndentationAwareStringWriter.serializeOperator(expression: Expression, left: Expression, right: Expression, type: String, redefines: String) {
        appendIndent("redefine contains $redefines: $type") {
            serialize(left, "left")
            serialize(right, "right")
        }
    }

    private fun IndentationAwareStringWriter.serialize(expression: NotOperator, redefines: String) {
        appendIndent("redefine contains $redefines: NotExpression") {
            serialize(expression.operand, "operand")
        }
    }

    private fun IndentationAwareStringWriter.serialize(expression: LiteralExpression, redefines: String) {
        when (expression) {
            is LiteralInteger -> appendIndent("redefine contains $redefines: LiteralIntegerExpression") {
                appendLine("redefine refers value: int = ${expression.value}")
            }
            is LiteralBoolean -> appendIndent("redefine contains $redefines: LiteralBooleanExpression") {
                appendLine("redefine refers value: bool = ${expression.isValue}")
            }
        }
    }

    private fun IndentationAwareStringWriter.serialize(expression: ReachabilityExpression, redefines: String) = when (expression) {
        is StateReachabilityExpression -> serialize(expression, redefines)
        else -> error("Unknown reachability expression: $expression")
    }

    private fun IndentationAwareStringWriter.serialize(expression: StateReachabilityExpression, redefines: String) {
        appendIndent("redefine contains $redefines: StateReachabilityExpression") {
            appendLine("redefine refers state: State = ${expression.expression.serialize()}")
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
