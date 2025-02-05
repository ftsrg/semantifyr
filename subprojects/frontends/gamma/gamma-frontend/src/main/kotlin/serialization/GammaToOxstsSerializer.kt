/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.gamma.frontend.serialization

import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.Action
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.ChainingExpression
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.Channel
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.Component
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.ComponentInstance
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.EntryTransition
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.Event
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.EventTrigger
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.InputEvent
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.OutputEvent
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.Package
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.RaiseEventAction
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.ReferenceExpression
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.Region
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.SetTimeoutAction
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.State
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.StateTransition
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.Statechart
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.SyncComponent
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.Timeout
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.TimeoutTrigger
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.Transition
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.Trigger
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.VerificationCase
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.IndentationAwareStringWriter
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.appendIndent
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.indent
import org.eclipse.emf.ecore.EObject
import kotlin.reflect.KProperty

object GammaToOxstsSerializer {

    fun serialize(gammaPackage: Package) = indent {
        appendLine("package ${gammaPackage.name}")
        appendLine()

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

            for (event in statechart.events) {
                serialize(event)
            }

            for (region in statechart.regions) {
                serialize(region)
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

            if (transition.trigger != null) {
                serialize(transition.trigger)
            }

            if (transition.action != null) {
                serialize(transition.action, "actions")
            }
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
        else -> error("Unknown action type: $action")
    }

    private fun IndentationAwareStringWriter.serialize(action: RaiseEventAction, subsets: String) {
        appendIndent("containment ${action.name} :> $subsets : RaiseEventAction") {
            appendLine("reference ::> event : Event = ${action.event.name}")
        }
    }

    private fun IndentationAwareStringWriter.serialize(action: SetTimeoutAction, subsets: String) {
        appendIndent("containment ${action.name} :> $subsets : SetTimeoutAction") {
            appendLine("reference ::> timeout : Timeout = ${action.timeout.name}")
            appendLine("reference ::> amount : Integer = ${action.value}")
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
                for (event in verificationCase.component.component.events.filterIsInstance<InputEvent>()) {
                    appendLine("inline ${verificationCase.component.name}.${event.name}.havoc()")
                }

                appendLine("inline ${verificationCase.component.name}.main()")
                appendLine("inline ${verificationCase.component.name}.passTime()")
            }

            appendIndent("prop") {
                appendLine("! (${verificationCase.state.elements.asSequence().take(verificationCase.state.elements.size - 1).toList().serialize()}.activeState == ${verificationCase.state.serialize()})")
            }
        }
    }

    private fun ChainingExpression.serialize(): String {
        return elements.serialize()
    }

    private fun List<ReferenceExpression>.serialize(): String {
        return map{
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

val EObject.name
    get() = when (this) {
        is Action -> name
        is Transition -> name
        is Trigger -> name
        is Channel -> name
        else -> {
            val nameFeature = eClass().getEStructuralFeature("name")
            eGet(nameFeature).toString()
        }
    }
