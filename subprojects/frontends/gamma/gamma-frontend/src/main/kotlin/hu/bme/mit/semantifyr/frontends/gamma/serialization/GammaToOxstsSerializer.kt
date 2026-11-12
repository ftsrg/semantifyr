/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.gamma.serialization

import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.AG
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.Action
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.ArithmeticBinaryOperator
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.ArithmeticOp
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.ArithmeticUnaryOperator
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.AssignmentAction
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.Block
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.BooleanOp
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.BooleanOperator
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.BooleanType
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.Channel
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.ComparisonOp
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.ComparisonOperator
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.ComponentDeclaration
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.ComponentInstance
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.DirectReferenceExpression
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.EF
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.EventDeclaration
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.EventDirection
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.EventTrigger
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.Expression
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.GammaModelPackage
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.GammaUtils
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.GuardEvaluation
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.InitialState
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.InstanceElementExpression
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.InstancePortReference
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.IntegerType
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.InterfaceDeclaration
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.LiteralBoolean
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.LiteralExpression
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.LiteralInteger
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.NamedElement
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.NegationOperator
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.PortBinding
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.PortDeclaration
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.RaiseEventAction
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.RealizationMode
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.Region
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.SchedulingOrder
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.SetTimeoutAction
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.State
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.StateNode
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.StateReferenceExpression
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.StatechartDeclaration
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.SyncComponentDeclaration
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.TemporalOperator
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.TimeUnit
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.TimeoutDeclaration
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.TimeoutTrigger
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.Transition
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.TransitionPriority
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.Trigger
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.Type
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.UnaryOp
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.VariableDeclaration
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.VariableReferenceExpression
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.VerificationCaseDeclaration
import hu.bme.mit.semantifyr.utils.text.IndentingStringBuilder
import hu.bme.mit.semantifyr.utils.text.buildIndented
import org.eclipse.emf.ecore.EObject
import org.eclipse.xtext.EcoreUtil2
import org.eclipse.xtext.nodemodel.util.NodeModelUtils

class GammaToOxstsSerializer(
    private val nameMap: GammaNameMap,
) {

    fun transformToOxsts(gammaPackage: GammaModelPackage) = buildIndented {
        UnmappedGammaConstructs.check(gammaPackage)
        appendLine("package ${markQualifiedName(gammaPackage.name)}")
        appendLine()
        appendLine("import semantifyr::gamma::expressions")
        appendLine("import semantifyr::gamma::variables")
        appendLine("import semantifyr::gamma::statecharts")
        appendLine("import semantifyr::gamma::components")
        appendLine("import semantifyr::gamma::triggers")
        appendLine("import semantifyr::gamma::actions")
        appendLine("import semantifyr::gamma::events")
        appendLine("import semantifyr::gamma::ports")
        appendLine("import semantifyr::gamma::verification")
        for (importStatement in gammaPackage.imports) {
            appendLine("import ${markQualifiedName(importStatement.importedPackage.name)}")
        }
        appendLine()

        for (interfaceDeclaration in gammaPackage.interfaces) {
            serialize(interfaceDeclaration)
        }

        for (component in gammaPackage.components) {
            serialize(component)
        }

        for (verificationCase in gammaPackage.verificationCases) {
            serialize(verificationCase)
        }
    }

    private fun IndentingStringBuilder.serialize(interfaceDeclaration: InterfaceDeclaration) {
        appendIndent("class ${markName(interfaceDeclaration.name)} : Interface") {
            for (event in interfaceDeclaration.events) {
                serialize(event)
            }
        }
    }

    private fun getEventDirection(event: EventDeclaration): String {
        return when (event.direction) {
            EventDirection.IN -> "inputEvents"
            EventDirection.OUT -> "outputEvents"
            EventDirection.INOUT -> error("Inout event '${event.name}' has no OXSTS mapping")
        }
    }

    private fun IndentingStringBuilder.serialize(event: EventDeclaration) {
        val name = nameMap.declare(event.eContainer(), event, markName(event.name))
        appendIndent("contains $name: Event subsets ${getEventDirection(event)}") {
            appendLine("@Shared")
            appendLine("redefine contains declaration: EventDeclaration")
        }
    }

    private fun IndentingStringBuilder.serialize(component: ComponentDeclaration) = when (component) {
        is SyncComponentDeclaration -> serialize(component)
        is StatechartDeclaration -> serialize(component)
        else -> error("Unknown component type: $component")
    }

    private fun IndentingStringBuilder.serialize(component: SyncComponentDeclaration) {
        appendIndent("class ${markName(component.name)} : SyncComponent") {
            val unboundPorts = mutableSetOf<PortDeclaration>()
            unboundPorts += component.ports
            unboundPorts -= component.bindings.map { it.port }.toSet()

            for (port in unboundPorts) {
                serialize(port)
            }

            for (portBinding in component.bindings) {
                serialize(portBinding)
            }

            for (instance in component.components) {
                serialize(instance)
            }

            for (channel in component.channels) {
                serialize(channel)
            }
        }
    }

    private fun getPortType(port: PortDeclaration): String {
        return when (port.realizationMode) {
            RealizationMode.PROVIDED -> "ProvidedPort"
            RealizationMode.REQUIRED -> "RequiredPort"
        }
    }

    private fun IndentingStringBuilder.serialize(port: PortDeclaration) {
        val name = nameMap.declare(port.eContainer(), port, markName(port.name))
        appendIndent("contains $name: ${getPortType(port)} subsets ports") {
            val interfaceName = nameMap.declare(port, port.`interface`, "interface")
            appendLine("redefine contains $interfaceName: ${qualifiedClassName(port.`interface`)}")
        }
    }

    private fun IndentingStringBuilder.serialize(portBinding: PortBinding) {
        val name = nameMap.declare(portBinding.eContainer(), portBinding.port, markName(portBinding.port.name))
        appendLine("refers $name: Port subsets ports = ${instancePortPath(portBinding.boundPort)}")
    }

    private fun IndentingStringBuilder.serialize(instance: ComponentInstance) {
        val name = nameMap.declareTyped(instance.eContainer(), instance, markName(instance.name), instance.component)
        appendLine("contains $name: ${qualifiedClassName(instance.component)} subsets components")
    }

    private fun IndentingStringBuilder.serialize(channel: Channel) {
        val name = nameMap.declareGenerated(channel.eContainer(), channel, deriveName(channel))
        appendIndent("contains $name: Channel subsets channels") {
            appendLine("redefine refers inputPort: Port = ${instancePortPath(channel.providedPort)}")
            appendLine("redefine refers outputPort: Port = ${instancePortPath(channel.requiredPort)}")
        }
    }

    private fun instancePortPath(reference: InstancePortReference): String {
        return "${markName(reference.instance.name)}.${markName(reference.port.name)}"
    }

    private fun qualifiedClassName(element: NamedElement): String {
        val gammaPackage = EcoreUtil2.getContainerOfType(element, GammaModelPackage::class.java)
        return "${markQualifiedName(gammaPackage.name)}::${markName(element.name)}"
    }

    private fun getRegionSchedule(schedulingOrder: SchedulingOrder): String {
        return when (schedulingOrder) {
            SchedulingOrder.TOP_DOWN -> "TopDown"
            SchedulingOrder.BOTTOM_UP -> "BottomUp"
        }
    }

    private fun getTransitionPriority(transitionPriority: TransitionPriority): String {
        return when (transitionPriority) {
            TransitionPriority.OFF -> "Off"
            TransitionPriority.ORDER_BASED -> "OrderBased"
            TransitionPriority.VALUE_BASED -> "ValueBased"
        }
    }

    private fun getGuardEvaluation(guardEvaluation: GuardEvaluation): String {
        return when (guardEvaluation) {
            GuardEvaluation.ON_THE_FLY -> "OnTheFly"
            GuardEvaluation.BEGINNING_OF_STEP -> "BeginningOfStep"
        }
    }

    private fun IndentingStringBuilder.serialize(statechart: StatechartDeclaration) {
        appendIndent("class ${markName(statechart.name)} : Statechart") {
            appendLine("redefine refers regionSchedule: RegionSchedule = RegionSchedule::${getRegionSchedule(statechart.schedulingOrder)}")
            appendLine("redefine refers transitionPriority: TransitionPriority = TransitionPriority::${getTransitionPriority(statechart.transitionPriority)}")
            appendLine("redefine refers guardEvaluation: GuardEvaluation = GuardEvaluation::${getGuardEvaluation(statechart.guardEvaluation)}")

            for (port in statechart.ports) {
                serialize(port)
            }

            for (variable in statechart.variables) {
                serialize(variable)
            }

            for (timeout in statechart.timeouts) {
                serialize(timeout)
            }

            for (region in statechart.regions) {
                serialize(region)
            }
        }
    }

    private fun getTypeReferenceName(typeReference: Type): String {
        return when (typeReference) {
            is IntegerType -> "Integer"
            is BooleanType -> "Boolean"
            else -> error("Unknown type reference!")
        }
    }

    private fun getTypeReferenceOxstsName(typeReference: Type): String {
        return when (typeReference) {
            is IntegerType -> "int"
            is BooleanType -> "bool"
            else -> error("Unknown type reference!")
        }
    }

    private fun IndentingStringBuilder.serialize(variable: VariableDeclaration) {
        val name = nameMap.declare(variable.eContainer(), variable, markName(variable.name))
        appendIndent("contains $name: ${getTypeReferenceName(variable.type)}Variable subsets variables") {
            if (variable.default != null) {
                val default = serializeInline(variable.default)

                appendLine("redefine refers defaultValue: ${getTypeReferenceOxstsName(variable.type)} = $default")
            }
        }
    }

    private fun IndentingStringBuilder.serialize(timeout: TimeoutDeclaration) {
        val name = nameMap.declare(timeout.eContainer(), timeout, markName(timeout.name))
        if (isSetInMilliseconds(timeout)) {
            appendIndent("contains $name: Timeout subsets timeouts") {
                appendLine("redefine refers deltaTime: int = $MILLISECONDS_PER_STEP")
            }
        } else {
            appendLine("contains $name: Timeout subsets timeouts")
        }
    }

    // the validator lets a timeout be set to one value only, so its settings share one unit
    private fun isSetInMilliseconds(timeout: TimeoutDeclaration): Boolean {
        val statechart = EcoreUtil2.getContainerOfType(timeout, StatechartDeclaration::class.java)
        return EcoreUtil2.getAllContentsOfType(statechart, SetTimeoutAction::class.java).any {
            it.timeout == timeout && it.unit == TimeUnit.MILLISECOND
        }
    }

    private fun IndentingStringBuilder.serialize(region: Region) {
        val name = nameMap.declare(region.eContainer(), region, markName(region.name))
        appendIndent("contains $name: Region subsets regions") {
            for (state in region.stateNodes.filterIsInstance<State>()) {
                serialize(state)
            }

            for (transition in transitionsOf(region)) {
                serialize(region, transition)
            }
        }
    }

    private fun IndentingStringBuilder.serialize(state: State) {
        val name = nameMap.declare(state.eContainer(), state, markName(state.name))
        appendIndent("contains $name: State subsets states") {
            for (action in state.entryActions) {
                serialize(action, "entryActions")
            }

            for (action in state.exitActions) {
                serialize(action, "exitActions")
            }

            for (region in state.regions) {
                serialize(region)
            }
        }
    }

    private fun transitionsOf(region: Region): List<Transition> {
        val statechart = EcoreUtil2.getContainerOfType(region, StatechartDeclaration::class.java)
        return statechart.transitions.filter {
            it.from.eContainer() == region
        }
    }

    private fun IndentingStringBuilder.serialize(region: Region, transition: Transition) {
        if (transition.from is InitialState) {
            serializeEntryTransition(region, transition)
        } else {
            serializeStateTransition(region, transition)
        }
    }

    private fun IndentingStringBuilder.serializeEntryTransition(region: Region, transition: Transition) {
        val name = nameMap.declareGenerated(region, transition, "entry_to_${transition.to.name}")
        appendIndent("contains $name: EntryTransition subsets entryTransitions") {
            appendLine("redefine refers to: State = ${markName(transition.to.name)}")
        }
    }

    private fun IndentingStringBuilder.serializeStateTransition(region: Region, transition: Transition) {
        val name = if (transition.name != null) {
            nameMap.declare(region, transition, markName(transition.name))
        } else {
            nameMap.declareGenerated(region, transition, deriveName(region, transition))
        }
        appendIndent("contains $name: Transition subsets transitions") {
            appendLine("redefine refers from: State = ${markName(transition.from.name)}")
            appendLine("redefine refers to: State = ${targetPath(transition).joinToString(".")}")

            val statechart = EcoreUtil2.getContainerOfType(transition, StatechartDeclaration::class.java)
            if (statechart.transitionPriority == TransitionPriority.VALUE_BASED && transition.priority != 0) {
                appendLine("redefine refers priority: int = ${transition.priority}")
            }

            if (transition.guard != null) {
                appendIndent("redefine contains guard: Guard") {
                    serialize(transition.guard, "expression")
                }
            }

            if (transition.trigger != null) {
                serialize(transition.trigger)
            }

            for (action in transition.actions) {
                serialize(action, "actions")
            }
        }
    }

    private fun IndentingStringBuilder.serialize(trigger: Trigger) = when (trigger) {
        is EventTrigger -> serialize(trigger)
        is TimeoutTrigger -> serialize(trigger)
        else -> error("Unknown trigger type: $trigger")
    }

    // a target in another region is named from the lowest region holding both ends, which the transition's own scope reaches
    private fun targetPath(transition: Transition): List<String> {
        val sourceRegions = ancestorRegions(transition.from)
        val path = mutableListOf(markName(transition.to.name))
        var region = transition.to.eContainer() as Region
        while (region !in sourceRegions) {
            val state = region.eContainer() as State
            path.add(0, markName(region.name))
            path.add(0, markName(state.name))
            region = state.eContainer() as Region
        }
        return path
    }

    private fun ancestorRegions(stateNode: StateNode): Set<Region> {
        val regions = mutableSetOf<Region>()
        var current: EObject = stateNode.eContainer()
        while (current is Region) {
            regions += current
            current = current.eContainer()
            if (current is State) {
                current = current.eContainer()
            }
        }
        return regions
    }

    private fun IndentingStringBuilder.serialize(trigger: EventTrigger) {
        appendIndent("redefine contains trigger: EventTrigger") {
            appendLine("redefine refers port: Port = ${markName(trigger.port.name)}")
            appendLine("redefine refers event: Event = ${eventPath(trigger.port, trigger.event)}")
        }
    }

    private fun eventPath(port: PortDeclaration, event: EventDeclaration): String {
        return "${markName(port.name)}.interface.${markName(event.name)}"
    }

    private fun IndentingStringBuilder.serialize(trigger: TimeoutTrigger) {
        appendIndent("redefine contains trigger: TimeoutTrigger") {
            appendLine("redefine refers timeout: Timeout = ${markName(trigger.timeout.name)}")
        }
    }

    private fun IndentingStringBuilder.serialize(
        action: Action,
        subsets: String,
    ): Unit = when (action) {
        is Block -> {
            for (inner in action.actions) {
                serialize(inner, subsets)
            }
        }
        is RaiseEventAction -> serialize(action, subsets)
        is SetTimeoutAction -> serialize(action, subsets)
        is AssignmentAction -> serialize(action, subsets)
        else -> error("Unknown action type: $action")
    }

    // a generated name starts with a lowercase word, so it never equals a marked Gamma name
    private fun deriveName(channel: Channel): String {
        val provided = "${channel.providedPort.instance.name}_${channel.providedPort.port.name}"
        val required = "${channel.requiredPort.instance.name}_${channel.requiredPort.port.name}"
        return "channel_${provided}_to_$required"
    }

    private fun deriveName(region: Region, transition: Transition): String {
        val base = "transition_${transition.from.name}_to_${transition.to.name}"
        val siblings = transitionsOf(region)
        val collides = siblings.any {
            it !== transition && it.name == null && it.from == transition.from && it.to == transition.to
        }
        if (!collides || transition.trigger == null) {
            return base
        }

        return "${base}_on_${deriveName(transition.trigger)}"
    }

    private fun deriveName(trigger: Trigger) = when (trigger) {
        is EventTrigger -> "${trigger.port.name}_${trigger.event.name}"
        is TimeoutTrigger -> trigger.timeout.name
        else -> error("Unknown trigger type: $trigger")
    }

    private fun deriveName(
        action: Action,
        subsets: String,
    ): String {
        val prefix = when (subsets) {
            "entryActions" -> "entry_"
            "exitActions" -> "exit_"
            else -> ""
        }
        val derived = when (action) {
            is RaiseEventAction -> "raise_${action.port.name}_${action.event.name}"
            is SetTimeoutAction -> "set_${action.timeout.name}"
            is AssignmentAction -> "assign_${assignedVariable(action).name}"
            else -> error("Unknown action type: $action")
        }
        return "$prefix$derived"
    }

    private fun IndentingStringBuilder.serialize(
        action: RaiseEventAction,
        subsets: String,
    ) {
        val name = nameMap.declareGenerated(action.eContainer(), action, deriveName(action, subsets))
        appendIndent("contains $name: RaiseEventAction subsets $subsets") {
            appendLine("redefine refers port: Port = ${markName(action.port.name)}")
            appendLine("redefine refers event: Event = ${eventPath(action.port, action.event)}")
        }
    }

    private fun IndentingStringBuilder.serialize(
        action: SetTimeoutAction,
        subsets: String,
    ) {
        val name = nameMap.declareGenerated(action.eContainer(), action, deriveName(action, subsets))
        appendIndent("contains $name: SetTimeoutAction subsets $subsets") {
            appendLine("redefine refers timeout: Timeout = ${markName(action.timeout.name)}")
            serialize(action.value, "expression")
        }
    }

    private fun IndentingStringBuilder.serialize(
        action: AssignmentAction,
        subsets: String,
    ) {
        val name = nameMap.declareGenerated(action.eContainer(), action, deriveName(action, subsets))
        appendIndent("contains $name: AssignmentAction subsets $subsets") {
            appendLine("redefine refers variable: Variable = ${markName(assignedVariable(action).name)}")
            serialize(action.expression, "expression")
        }
    }

    private fun assignedVariable(action: AssignmentAction): VariableDeclaration {
        return (action.reference as DirectReferenceExpression).declaration as VariableDeclaration
    }

    private fun IndentingStringBuilder.serialize(verificationCase: VerificationCaseDeclaration) {
        appendLine("@VerificationCase")
        appendIndent("class ${markName(verificationCase.name)} : GammaVerificationCase") {
            val component = verificationCase.component
            nameMap.declareTyped(verificationCase, component, CASE_COMPONENT, component)
            appendLine("redefine contains $CASE_COMPONENT: ${qualifiedClassName(component)}")

            serialize(verificationCase.expression, "propertyExpression")
        }
    }

    private fun IndentingStringBuilder.serialize(
        expression: Expression,
        redefines: String,
    ) = when (expression) {
        is ArithmeticBinaryOperator -> serialize(expression, redefines)
        is ArithmeticUnaryOperator -> serialize(expression, redefines)
        is BooleanOperator -> serialize(expression, redefines)
        is TemporalOperator -> serialize(expression, redefines)
        is ComparisonOperator -> serialize(expression, redefines)
        is LiteralExpression -> serialize(expression, redefines)
        is NegationOperator -> serialize(expression, redefines)
        is StateReferenceExpression -> serialize(expression, redefines)
        is DirectReferenceExpression -> serializeVariableReference(expression, redefines)
        is VariableReferenceExpression -> serializeVariableReference(expression, redefines)
        else -> error("Unknown type of expression: $expression")
    }

    private fun getOperatorName(operator: ArithmeticOp): String {
        return when (operator) {
            ArithmeticOp.ADD -> "PlusOperatorExpression"
            ArithmeticOp.SUB -> "MinusOperatorExpression"
            ArithmeticOp.MUL -> "MultiplyOperatorExpression"
            ArithmeticOp.DIV -> "DivideOperatorExpression"
        }
    }

    private fun getOperatorName(operator: UnaryOp): String {
        return when (operator) {
            UnaryOp.PLUS -> "UnaryPlusExpression"
            UnaryOp.MINUS -> "UnaryMinusExpression"
        }
    }

    private fun getOperatorName(operator: BooleanOp): String {
        return when (operator) {
            BooleanOp.AND -> "AndOperatorExpression"
            BooleanOp.OR -> "OrOperatorExpression"
        }
    }

    private fun getOperatorName(operator: ComparisonOp): String {
        return when (operator) {
            ComparisonOp.LESS -> "LessThanOperatorExpression"
            ComparisonOp.LESS_EQ -> "LessThanOrEqualsOperatorExpression"
            ComparisonOp.GREATER -> "GreaterThanOperatorExpression"
            ComparisonOp.GREATER_EQ -> "GreaterThanOrEqualsOperatorExpression"
            ComparisonOp.EQ -> "EqualityOperatorExpression"
            ComparisonOp.NOT_EQ -> "InequalityOperatorExpression"
        }
    }

    private fun IndentingStringBuilder.serialize(
        expression: ArithmeticBinaryOperator,
        redefines: String,
    ) {
        serializeOperator(expression.left, expression.right, getOperatorName(expression.op), redefines)
    }

    private fun IndentingStringBuilder.serialize(
        expression: ArithmeticUnaryOperator,
        redefines: String,
    ) {
        serializeOperator(expression.body, getOperatorName(expression.op), redefines)
    }

    private fun IndentingStringBuilder.serialize(
        expression: BooleanOperator,
        redefines: String,
    ) {
        serializeOperator(expression.left, expression.right, getOperatorName(expression.op), redefines)
    }

    private fun IndentingStringBuilder.serialize(
        expression: TemporalOperator,
        redefines: String,
    ) {
        when (expression) {
            is AG -> serialize(expression, redefines)
            is EF -> serialize(expression, redefines)
            else -> error("Unknown type of temporal expression: $expression")
        }
    }

    private fun IndentingStringBuilder.serialize(
        expression: AG,
        redefines: String,
    ) {
        serializeOperator(expression.body, "MustAlways", redefines)
    }

    private fun IndentingStringBuilder.serialize(
        expression: EF,
        redefines: String,
    ) {
        serializeOperator(expression.body, "Eventually", redefines)
    }

    private fun IndentingStringBuilder.serialize(
        expression: ComparisonOperator,
        redefines: String,
    ) {
        serializeOperator(expression.left, expression.right, getOperatorName(expression.op), redefines)
    }

    private fun IndentingStringBuilder.serializeOperator(
        left: Expression,
        right: Expression,
        name: String,
        redefines: String,
    ) {
        appendIndent("redefine contains $redefines: $name") {
            serialize(left, "left")
            serialize(right, "right")
        }
    }

    private fun IndentingStringBuilder.serializeOperator(
        body: Expression,
        name: String,
        redefines: String,
    ) {
        appendIndent("redefine contains $redefines: $name") {
            serialize(body, "body")
        }
    }

    private fun IndentingStringBuilder.serialize(
        expression: LiteralExpression,
        redefines: String,
    ) {
        when (expression) {
            is LiteralInteger -> serialize(expression, redefines)
            is LiteralBoolean -> serialize(expression, redefines)
            else -> error("")
        }
    }

    private fun IndentingStringBuilder.serialize(
        expression: LiteralInteger,
        redefines: String,
    ) {
        appendIndent("redefine contains $redefines: LiteralIntegerExpression") {
            appendLine("redefine refers value: int = ${expression.value}")
        }
    }

    private fun IndentingStringBuilder.serialize(
        expression: LiteralBoolean,
        redefines: String,
    ) {
        appendIndent("redefine contains $redefines: LiteralBooleanExpression") {
            appendLine("redefine refers value: bool = ${expression.isValue}")
        }
    }

    private fun IndentingStringBuilder.serialize(
        expression: NegationOperator,
        redefines: String,
    ) {
        serializeOperator(expression.body, "UnaryNotExpression", redefines)
    }

    private fun IndentingStringBuilder.serialize(
        expression: StateReferenceExpression,
        redefines: String,
    ) {
        val path = instancePath(expression) + containmentPath(expression.state)
        appendIndent("redefine contains $redefines: IsStateActiveExpression") {
            appendLine("redefine refers state: State = ${path.joinToString(".")}")
        }
    }

    private fun IndentingStringBuilder.serializeVariableReference(
        expression: DirectReferenceExpression,
        redefines: String,
    ) {
        appendIndent("redefine contains $redefines: VariableExpression") {
            appendLine("redefine refers variable: Variable = ${markName(expression.declaration.name)}")
        }
    }

    private fun IndentingStringBuilder.serializeVariableReference(
        expression: VariableReferenceExpression,
        redefines: String,
    ) {
        val path = instancePath(expression) + markName(expression.variable.name)
        appendIndent("redefine contains $redefines: VariableExpression") {
            appendLine("redefine refers variable: Variable = ${path.joinToString(".")}")
        }
    }

    private fun instancePath(expression: InstanceElementExpression): List<String> {
        return listOf(CASE_COMPONENT) + GammaUtils.getInstancePath(expression).map {
            markName(it.name)
        }
    }

    private fun containmentPath(element: EObject): List<String> {
        val path = mutableListOf<String>()
        var current = element
        while (current !is StatechartDeclaration) {
            path.add(0, markName((current as NamedElement).name))
            current = current.eContainer()
        }
        return path
    }

    private fun serializeInline(expression: Expression): String {
        val node = NodeModelUtils.getNode(expression)
        return NodeModelUtils.getTokenText(node)
    }

    companion object {
        private const val CASE_COMPONENT = "component"
        private const val MILLISECONDS_PER_STEP = 1000
    }
}
