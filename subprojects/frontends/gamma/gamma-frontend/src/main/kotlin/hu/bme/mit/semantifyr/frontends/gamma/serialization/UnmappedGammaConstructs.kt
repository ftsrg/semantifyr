/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.gamma.serialization

import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.ArithmeticUnaryOperator
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.AsynchronousAdapterDeclaration
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.Channel
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.ClockDeclaration
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.ComponentDeclaration
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.ComponentInstance
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.CompositeComponentDeclaration
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.ControlFunction
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.ControlSpecification
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.EventParameterReferenceExpression
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.Expression
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.FieldAssignment
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.FiredTransitionExpression
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.GammaModelPackage
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.GammaPackage
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.GammaUtils
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.InitialState
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.InstanceElementExpression
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.LiteralExpression
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.MessageQueue
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.RaiseEventAction
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.RecordLiteralExpression
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.RecordType
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.Region
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.ShallowHistoryState
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.State
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
    val reason: String = "is not mapped to OXSTS yet",
) {
    val location: String
        get() {
            val file = element.eResource()?.uri?.lastSegment() ?: "<unknown>"
            val line = NodeModelUtils.getNode(element)?.startLine ?: 0
            return "$file:$line"
        }

    override fun toString(): String {
        return "$location: $construct $reason"
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
        val constructs = EcoreUtil2.eAllContentsAsList(gammaPackage).mapNotNull {
            describe(it)
        }
        if (constructs.isNotEmpty()) {
            return constructs
        }
        return gammaPackage.components.flatMap {
            describeAsynchronousComposition(it)
        } + gammaPackage.verificationCases.mapNotNull {
            describeAsynchronousRoot(it)
        }
    }

    // at the root every port is the environment's, and Gamma's transformation rejects a queue mixing them with clock ticks
    private fun describeAsynchronousRoot(verificationCase: VerificationCaseDeclaration): UnmappedGammaConstruct? {
        val component = verificationCase.component
        if (component == null || !AsynchronousMapping.isAdapter(component)) {
            return null
        }
        val queue = AsynchronousMapping.adapterOf(component).queues.firstOrNull {
            it.storesClocks && it.portMessages.isNotEmpty()
        } ?: return null
        return UnmappedGammaConstruct(
            verificationCase,
            "the queue '${queue.name}' of '${component.name}' storing messages of the environment and clock ticks",
        )
    }

    private fun describe(element: EObject): UnmappedGammaConstruct? {
        val construct = when (element) {
            is RecordType -> {
                if (element.eContainer() !is TypeDeclaration) {
                    "an anonymous record type"
                } else {
                    null
                }
            }
            is Channel -> {
                return describe(element)
            }
            is VariableDeclaration -> {
                describe(element)
            }
            is ShallowHistoryState -> {
                describe(element)
            }
            is Transition -> {
                return describe(element)
            }
            is RaiseEventAction -> {
                describe(element)
            }
            is RecordLiteralExpression -> {
                if (!isInitialValue(element)) {
                    "a record literal outside a variable's initial value"
                } else {
                    null
                }
            }
            is EventParameterReferenceExpression -> {
                if (isInVerificationCase(element)) {
                    "the event parameter reference '${element.parameter?.name}' in a property"
                } else {
                    null
                }
            }
            is FiredTransitionExpression -> {
                "a fired transition in a property"
            }
            is ControlSpecification -> {
                return describe(element)
            }
            is MessageQueue -> {
                describe(element)
            }
            is ClockDeclaration -> {
                if (GammaUtils.evaluateInteger(element.value) == null) {
                    "the rate of clock '${element.name}' other than a constant"
                } else {
                    null
                }
            }
            is StatechartDeclaration -> {
                describeAsynchronous(element)
            }
            is ComponentInstance -> {
                describeAsynchronous(element)
            }
            is InstanceElementExpression -> {
                if (!isInVerificationCase(element)) {
                    "a component instance reference in a statechart"
                } else {
                    null
                }
            }
            else -> {
                null
            }
        }
        return construct?.let {
            UnmappedGammaConstruct(element, it)
        }
    }

    // Gamma's XSTS route consumes a full-step message without running the component, its code generator runs it to completion
    private fun describe(specification: ControlSpecification): UnmappedGammaConstruct? {
        return when (specification.controlFunction) {
            ControlFunction.RUN_TO_COMPLETION -> UnmappedGammaConstruct(specification, "the full-step control function", "is refused")
            ControlFunction.RESET -> UnmappedGammaConstruct(specification, "the reset control function")
            else -> null
        }
    }

    private fun describe(queue: MessageQueue): String? {
        if (GammaUtils.evaluateInteger(queue.capacity) == null) {
            return "the capacity of queue '${queue.name}' other than a constant"
        }
        val adapter = queue.eContainer() as AsynchronousAdapterDeclaration
        val mapping = AsynchronousMapping.adapterOf(adapter).queues.firstOrNull {
            it.element == queue
        } ?: return null
        return describeMessages(mapping, "queue '${queue.name}'")
    }

    private fun describeMessages(queue: QueueMapping, owner: String): String? {
        val recordMessage = queue.portMessages.firstOrNull { message ->
            message.event!!.parameters.any {
                GammaUtils.resolveType(it.type) is RecordType
            }
        }
        if (recordMessage != null) {
            return "the record-typed parameter of '${recordMessage.event!!.name}' stored in $owner"
        }
        val mismatched = queue.messages.firstOrNull {
            it.targetEvent != null && it.targetEvent.parameters.size != it.event!!.parameters.size
        }
        if (mismatched != null) {
            return "the passing of '${mismatched.event!!.name}' to '${mismatched.targetEvent!!.name}' with other parameters in $owner"
        }
        return null
    }

    private fun describeAsynchronous(statechart: StatechartDeclaration): String? {
        if (!statechart.isAsynchronous) {
            return null
        }
        if (statechart.capacity != null && GammaUtils.evaluateInteger(statechart.capacity) == null) {
            return "the capacity of statechart '${statechart.name}' other than a constant"
        }
        val queue = AsynchronousMapping.adapterOf(statechart).queues.singleOrNull() ?: return null
        return describeMessages(queue, "the queue of statechart '${statechart.name}'")
    }

    private fun describeAsynchronous(instance: ComponentInstance): String? {
        val container = instance.eContainer() as? CompositeComponentDeclaration ?: return null
        if (!GammaUtils.isAsynchronous(container) || AsynchronousMapping.isAdapter(instance.component)) {
            return null
        }
        return "the asynchronous composite '${instance.component.name}' inside the asynchronous composite '${container.name}'"
    }

    // a composite decides per adapter instance which queues the environment fills
    private fun describeAsynchronousComposition(component: ComponentDeclaration): List<UnmappedGammaConstruct> {
        if (component !is CompositeComponentDeclaration || !GammaUtils.isAsynchronous(component)) {
            return emptyList()
        }
        val constructs = mutableListOf<UnmappedGammaConstruct>()
        for (instance in AsynchronousMapping.adapterInstances(component)) {
            for (queue in AsynchronousMapping.adapterOf(instance.component).queues) {
                val bound = queue.portMessages.count {
                    AsynchronousMapping.isBound(component, instance, it.port!!)
                }
                if (bound > 0 && (bound < queue.portMessages.size || queue.storesClocks)) {
                    val others = if (queue.storesClocks) {
                        "clock ticks"
                    } else {
                        "messages of other components"
                    }
                    constructs += UnmappedGammaConstruct(
                        instance,
                        "the queue '${queue.name}' of '${instance.name}' storing messages of the environment and $others",
                    )
                }
            }
        }
        return constructs
    }

    private fun isInVerificationCase(element: EObject): Boolean {
        return EcoreUtil2.getContainerOfType(element, VerificationCaseDeclaration::class.java) != null
    }

    private fun isInitialValue(literal: RecordLiteralExpression): Boolean {
        var current: EObject = literal
        while (current.eContainer() is FieldAssignment) {
            current = current.eContainer().eContainer()
        }
        return current.eContainingFeature() == GammaPackage.Literals.VARIABLE_DECLARATION__DEFAULT
    }

    // Gamma's validator only warns on a channel that connects an instance to itself, the library has no such channel
    private fun describe(channel: Channel): UnmappedGammaConstruct? {
        val instance = channel.providedPort.instance
        if (instance != channel.requiredPort.instance) {
            return null
        }
        return UnmappedGammaConstruct(channel, "a channel from the instance '${instance.name}' to itself", "is refused")
    }

    private fun describe(variable: VariableDeclaration): String? {
        val default = variable.default ?: return null
        val recordType = GammaUtils.resolveType(variable.type) as? RecordType
        if (recordType == null) {
            if (!isLiteral(default)) {
                return "the non-literal initial value of variable '${variable.name}'"
            }
            return null
        }
        if (default !is RecordLiteralExpression || GammaUtils.resolveType(default.typeDeclaration.type) != recordType || !hasLiteralLeaves(default)) {
            return "the initial value of record variable '${variable.name}' other than a record literal of literals"
        }
        return null
    }

    private fun isLiteral(expression: Expression): Boolean {
        return expression is LiteralExpression || (expression is ArithmeticUnaryOperator && expression.body is LiteralExpression)
    }

    private fun hasLiteralLeaves(literal: RecordLiteralExpression): Boolean {
        return literal.fieldAssignments.all {
            val value = it.value
            if (value is RecordLiteralExpression) {
                hasLiteralLeaves(value)
            } else {
                isLiteral(value)
            }
        }
    }

    private fun describe(history: ShallowHistoryState): String? {
        val region = history.eContainer() as Region
        if (region.stateNodes.filterIsInstance<ShallowHistoryState>().first() != history) {
            return "a second history node '${history.name}' in region '${region.name}'"
        }
        val statechart = EcoreUtil2.getContainerOfType(history, StatechartDeclaration::class.java)
        val transitions = statechart.transitions.filter {
            it.from == history
        }
        val transition = transitions.singleOrNull()
        if (transition == null || transition.to !is State || transition.to.eContainer() != region) {
            return "the history node '${history.name}' without one transition to a state of its region"
        }
        if (transition.trigger != null || transition.guard != null || transition.actions.isNotEmpty()) {
            return "the transition of the history node '${history.name}' with a trigger, guard or action"
        }
        return null
    }

    private fun describe(action: RaiseEventAction): String? {
        if (action.arguments.size != action.event.parameters.size) {
            return "a raise of '${action.event.name}' with ${action.arguments.size} arguments for ${action.event.parameters.size} parameters"
        }
        return null
    }

    private fun describe(transition: Transition): UnmappedGammaConstruct? {
        val history = transition.to
        if (history is ShallowHistoryState) {
            val parentState = history.eContainer().eContainer()
            if (parentState !is State) {
                return UnmappedGammaConstruct(transition, "a transition to the history node '${history.name}' of a top-level region")
            }
            // Gamma's validator accepts it and its transformation leaves the transition out
            if (parentState in ancestors(transition.from) && history.eContainer() !in ancestors(transition.from)) {
                return UnmappedGammaConstruct(
                    transition,
                    "a transition from '${transition.from.name}' to the history node '${history.name}' from an orthogonal region of its parent state",
                    "is refused",
                )
            }
        }
        val construct = describeEnds(transition) ?: return null
        return UnmappedGammaConstruct(transition, construct)
    }

    private fun describeEnds(transition: Transition): String? {
        if (transition.from is InitialState) {
            if (transition.trigger != null || transition.guard != null) {
                return "an initial transition with a trigger or guard"
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
