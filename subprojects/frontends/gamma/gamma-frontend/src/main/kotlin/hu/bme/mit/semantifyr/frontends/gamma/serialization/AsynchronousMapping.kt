/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.gamma.serialization

import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.AnyPortEventReference
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.AnyTrigger
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.AsynchronousAdapterDeclaration
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.ClockDeclaration
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.ClockTickReference
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.ComponentDeclaration
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.ComponentInstance
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.CompositeComponentDeclaration
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.ControlFunction
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.ControlSpecification
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.EventDeclaration
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.GammaUtils
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.MessageQueue
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.MessageReference
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.MessageTrigger
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.PortDeclaration
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.PortEventReference
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.ScheduledAsynchronousCompositeComponentDeclaration
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.StatechartDeclaration
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.TimeUnit
import org.eclipse.emf.ecore.EObject

enum class MessageControl {
    NONE,
    RUN,
    CLEAR_QUEUE,
    CLEAR_QUEUES,
    CLEAR_OTHER_QUEUES,
}

class QueuedMessage(
    val identifier: Int,
    val port: PortDeclaration?,
    val event: EventDeclaration?,
    val clock: ClockDeclaration?,
    val targetPort: PortDeclaration?,
    val targetEvent: EventDeclaration?,
    val control: MessageControl,
)

class QueueMapping(
    val element: EObject,
    val name: String,
    val capacity: Int,
    val priority: Int,
    val messages: List<QueuedMessage>,
) {
    val storesClocks: Boolean
        get() {
            return messages.any {
                it.clock != null
            }
        }

    val portMessages: List<QueuedMessage>
        get() {
            return messages.filter {
                it.port != null
            }
        }
}

class ClockMapping(
    val clock: ClockDeclaration,
    val rate: Int,
    val deltaTime: Int,
)

class AdapterMapping(
    val component: ComponentDeclaration,
    val wrappedComponent: ComponentDeclaration,
    val clocks: List<ClockMapping>,
    val queues: List<QueueMapping>,
) {
    fun queueStoring(port: PortDeclaration, event: EventDeclaration): QueueMapping? {
        return queues.firstOrNull { queue ->
            queue.messages.any {
                it.port == port && it.event == event
            }
        }
    }
}

class DispatchMapping(
    val sourceInstance: ComponentInstance,
    val sourcePort: PortDeclaration,
    val event: EventDeclaration,
    val targetInstance: ComponentInstance,
    val targetPort: PortDeclaration,
    val queue: QueueMapping,
    val message: QueuedMessage,
    val resetsSource: Boolean,
)

object AsynchronousMapping {

    const val DEFAULT_CAPACITY = 4
    const val MILLISECONDS_PER_STEP = 1000

    fun isAdapter(component: ComponentDeclaration): Boolean {
        return component is AsynchronousAdapterDeclaration || (component is StatechartDeclaration && component.isAsynchronous)
    }

    fun adapterOf(component: ComponentDeclaration): AdapterMapping {
        return when (component) {
            is AsynchronousAdapterDeclaration -> adapterOf(component)
            is StatechartDeclaration -> defaultAdapterOf(component)
            else -> error("'${component.name}' is not an adapter")
        }
    }

    // Gamma's unfolding of an asynchronous statechart: one queue of every input, run on any message
    private fun defaultAdapterOf(statechart: StatechartDeclaration): AdapterMapping {
        val capacity = statechart.capacity?.let {
            GammaUtils.evaluateInteger(it) ?: 0
        } ?: DEFAULT_CAPACITY
        val name = if (statechart.capacity != null) {
            "${statechart.name}Queue"
        } else {
            "${statechart.name}MessageQueue"
        }
        val storedEvents = statechart.ports.flatMap { port ->
            GammaUtils.getInputEvents(port).map {
                port to it
            }
        }
        val messages = storedEvents.mapIndexed { index, (port, event) ->
            QueuedMessage(index + 1, port, event, null, port, event, MessageControl.RUN)
        }
        val queues = if (capacity > 0) {
            listOf(QueueMapping(statechart, name, capacity, 1, messages))
        } else {
            emptyList()
        }
        return AdapterMapping(statechart, statechart, emptyList(), queues)
    }

    private fun adapterOf(adapter: AsynchronousAdapterDeclaration): AdapterMapping {
        val wrappedComponent = adapter.wrappedComponent.component
        val clocks = adapter.clocks.map {
            val value = GammaUtils.evaluateInteger(it.value) ?: 0
            val deltaTime = if (it.unit == TimeUnit.MILLISECOND) {
                MILLISECONDS_PER_STEP
            } else {
                1
            }
            ClockMapping(it, value, deltaTime)
        }
        val functioning = adapter.messageQueues.filter {
            capacityOf(it) > 0
        }
        val queues = functioning.map {
            queueOf(adapter, it, functioning)
        }
        return AdapterMapping(adapter, wrappedComponent, clocks, queues)
    }

    // a capacity other than a constant is refused before mapping, a queue of no capacity is ignored as in Gamma
    fun capacityOf(queue: MessageQueue): Int {
        return GammaUtils.evaluateInteger(queue.capacity) ?: 0
    }

    private fun queueOf(
        adapter: AsynchronousAdapterDeclaration,
        queue: MessageQueue,
        functioning: List<MessageQueue>,
    ): QueueMapping {
        val storedEvents = LinkedHashSet<Pair<PortDeclaration, EventDeclaration>>()
        val storedClocks = LinkedHashSet<ClockDeclaration>()
        for (passing in queue.eventPassings) {
            when (val source = passing.source) {
                is ClockTickReference -> storedClocks += source.clock
                else -> storedEvents += inputEventsOf(source)
            }
        }
        val wrappedPorts = adapter.wrappedComponent.component.ports.toSet()
        val messages = mutableListOf<QueuedMessage>()
        for ((port, event) in storedEvents) {
            val target = targetOf(queue, port, event)
            val delivered = target?.takeIf {
                it.first in wrappedPorts
            }
            val control = controlOf(adapter, functioning) {
                matches(it, port, event)
            }
            messages += QueuedMessage(messages.size + 1, port, event, null, delivered?.first, delivered?.second, control)
        }
        for (clock in storedClocks) {
            val control = controlOf(adapter, functioning) {
                it is ClockTickReference && it.clock == clock
            }
            messages += QueuedMessage(messages.size + 1, null, null, clock, null, null, control)
        }
        return QueueMapping(queue, queue.name, capacityOf(queue), queue.priority, messages)
    }

    private fun inputEventsOf(reference: MessageReference): List<Pair<PortDeclaration, EventDeclaration>> {
        return when (reference) {
            is AnyPortEventReference -> GammaUtils.getInputEvents(reference.port).map {
                reference.port to it
            }
            is PortEventReference -> {
                if (GammaUtils.isInputEvent(reference.port, reference.event)) {
                    listOf(reference.port to reference.event)
                } else {
                    emptyList()
                }
            }
            else -> emptyList()
        }
    }

    // the first passing storing the event decides where it is delivered, as Gamma's getEventPassing does
    private fun targetOf(
        queue: MessageQueue,
        port: PortDeclaration,
        event: EventDeclaration,
    ): Pair<PortDeclaration, EventDeclaration>? {
        val passing = queue.eventPassings.first {
            (port to event) in inputEventsOf(it.source)
        }
        return when (val target = passing.target) {
            null -> port to event
            is PortEventReference -> target.port to target.event
            is AnyPortEventReference -> {
                val index = GammaUtils.getInputEvents(port).indexOf(event)
                GammaUtils.getInputEvents(target.port).getOrNull(index)?.let {
                    target.port to it
                }
            }
            else -> null
        }
    }

    private fun matches(reference: MessageReference, port: PortDeclaration, event: EventDeclaration): Boolean {
        return when (reference) {
            is AnyPortEventReference -> reference.port == port && GammaUtils.isInputEvent(port, event)
            is PortEventReference -> reference.port == port && reference.event == event
            else -> false
        }
    }

    // a queue reset comes before a component reset, which comes before a run, as in Gamma's adapter transformation
    private fun controlOf(
        adapter: AsynchronousAdapterDeclaration,
        functioning: List<MessageQueue>,
        matches: (MessageReference) -> Boolean,
    ): MessageControl {
        val functions = adapter.controlSpecifications.filter {
            isTriggeredBy(it, matches)
        }.map {
            it.controlFunction
        }.toSet()
        val clearsQueue = ControlFunction.RESET_MESSAGE_QUEUE in functions
        val clearsOthers = ControlFunction.RESET_OTHER_MESSAGE_QUEUES in functions && functioning.size > 1
        if (ControlFunction.RESET_MESSAGE_QUEUES in functions || (clearsQueue && clearsOthers)) {
            return MessageControl.CLEAR_QUEUES
        }
        if (clearsQueue) {
            return MessageControl.CLEAR_QUEUE
        }
        if (clearsOthers) {
            return MessageControl.CLEAR_OTHER_QUEUES
        }
        if (ControlFunction.RUN_ONCE in functions) {
            return MessageControl.RUN
        }
        return MessageControl.NONE
    }

    private fun isTriggeredBy(specification: ControlSpecification, matches: (MessageReference) -> Boolean): Boolean {
        return when (val trigger = specification.trigger) {
            is AnyTrigger -> true
            is MessageTrigger -> matches(trigger.reference)
            else -> false
        }
    }

    fun adapterInstances(composite: CompositeComponentDeclaration): List<ComponentInstance> {
        return composite.components.filter {
            isAdapter(it.component)
        }
    }

    fun scheduleCount(composite: CompositeComponentDeclaration, instance: ComponentInstance): Int {
        if (composite !is ScheduledAsynchronousCompositeComponentDeclaration || composite.executionList.isEmpty()) {
            return 1
        }
        return composite.executionList.count {
            it == instance
        }
    }

    fun isBound(composite: CompositeComponentDeclaration, instance: ComponentInstance, port: PortDeclaration): Boolean {
        return composite.bindings.any {
            it.boundPort.instance == instance && it.boundPort.port == port
        }
    }

    fun dispatchesOf(composite: CompositeComponentDeclaration): List<DispatchMapping> {
        val dispatches = mutableListOf<DispatchMapping>()
        for (instance in adapterInstances(composite)) {
            for (port in GammaUtils.getAllPorts(instance.component)) {
                for (event in GammaUtils.getOutputEvents(port)) {
                    for (channel in composite.channels) {
                        val ends = listOf(
                            channel.providedPort to channel.requiredPort,
                            channel.requiredPort to channel.providedPort,
                        )
                        for ((source, target) in ends) {
                            if (source.instance != instance || source.port != port) {
                                continue
                            }
                            if (!isAdapter(target.instance.component) || !GammaUtils.isInputEvent(target.port, event)) {
                                continue
                            }
                            val queue = adapterOf(target.instance.component).queueStoring(target.port, event) ?: continue
                            val message = queue.messages.first {
                                it.port == target.port && it.event == event
                            }
                            val resetsSource = !isBound(composite, instance, port)
                            dispatches += DispatchMapping(instance, port, event, target.instance, target.port, queue, message, resetsSource)
                        }
                    }
                }
            }
        }
        return dispatches
    }
}
