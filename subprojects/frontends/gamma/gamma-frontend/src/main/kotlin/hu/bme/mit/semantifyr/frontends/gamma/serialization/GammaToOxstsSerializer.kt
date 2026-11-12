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
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.BooleanOp
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.BooleanOperator
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.BooleanType
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.Channel
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.ComparisonOp
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.ComparisonOperator
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.ComponentDeclaration
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.ComponentInstance
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.EF
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.Block
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.DirectReferenceExpression
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.EventDeclaration
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.EventDirection
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.EventTrigger
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.Expression
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.GammaModelPackage
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.GammaUtils
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.InitialState
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.InstanceElementExpression
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
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.StateReferenceExpression
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.StatechartDeclaration
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.SyncComponentDeclaration
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.TemporalOperator
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.TimeoutDeclaration
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.TimeoutTrigger
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.Transition
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

class GammaToOxstsSerializer {
    val nameStore = GammaNameStore()

    fun transformToOxsts(gammaPackage: GammaModelPackage) = buildIndented {
        UnmappedGammaConstructs.check(gammaPackage)
        appendLine("package ${gammaPackage.name}")
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
        appendIndent("class ${interfaceDeclaration.name} : Interface") {
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
        val name = nameStore.declare(event.eContainer(), event, event.name)
        appendLine("@Shared")
        appendLine("contains $name: Event subsets ${getEventDirection(event)}")
    }

    private fun IndentingStringBuilder.serialize(component: ComponentDeclaration) = when (component) {
        is SyncComponentDeclaration -> serialize(component)
        is StatechartDeclaration -> serialize(component)
        else -> error("Unknown component type: $component")
    }

    private fun IndentingStringBuilder.serialize(component: SyncComponentDeclaration) {
        appendIndent("class ${component.name} : SyncComponent") {
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
        val name = nameStore.declare(port.eContainer(), port, port.name)
        appendIndent("contains $name: ${getPortType(port)} subsets ports") {
            val interfaceName = nameStore.declare(port, port.`interface`, "interface")
            appendLine("redefine contains $interfaceName: ${port.`interface`.name}")
        }
    }

    private fun IndentingStringBuilder.serialize(portBinding: PortBinding) {
        val name = nameStore.declare(portBinding.eContainer(), portBinding.port, portBinding.port.name)
        appendLine(
            "refers $name: Port subsets ports = ${portBinding.boundPort.instance.name}.${portBinding.boundPort.port.name}",
        )
    }

    private fun IndentingStringBuilder.serialize(instance: ComponentInstance) {
        val name = nameStore.declareTyped(instance.eContainer(), instance, instance.name, instance.component)
        appendLine("contains $name: ${instance.component.name} subsets components")
    }

    private fun IndentingStringBuilder.serialize(channel: Channel) {
        val name = nameStore.declareGenerated(channel.eContainer(), channel, deriveName(channel))
        appendIndent("contains $name: Channel subsets channels") {
            appendLine("redefine refers inputPort: Port = ${channel.providedPort.instance.name}.${channel.providedPort.port.name}")
            appendLine("redefine refers outputPort: Port = ${channel.requiredPort.instance.name}.${channel.requiredPort.port.name}")
        }
    }

    private fun IndentingStringBuilder.serialize(statechart: StatechartDeclaration) {
        appendIndent("class ${statechart.name} : Statechart") {
            if (statechart.schedulingOrder == SchedulingOrder.BOTTOM_UP) {
                appendLine("redefine refers regionSchedule: RegionSchedule = RegionSchedule::BottomUp")
            } else if (statechart.schedulingOrder == SchedulingOrder.TOP_DOWN) {
                appendLine("redefine refers regionSchedule: RegionSchedule = RegionSchedule::TopDown")
            }

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
        val name = nameStore.declare(variable.eContainer(), variable, variable.name)
        appendIndent("contains $name: ${getTypeReferenceName(variable.type)}Variable subsets variables") {
            if (variable.default != null) {
                val default = serializeInline(variable.default)

                appendLine("redefine refers defaultValue: ${getTypeReferenceOxstsName(variable.type)} = $default")
            }
        }
    }

    private fun IndentingStringBuilder.serialize(timeout: TimeoutDeclaration) {
        val name = nameStore.declare(timeout.eContainer(), timeout, timeout.name)
        appendLine("contains $name: Timeout subsets timeouts")
    }

    private fun IndentingStringBuilder.serialize(region: Region) {
        val name = nameStore.declare(region.eContainer(), region, region.name)
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
        val name = nameStore.declare(state.eContainer(), state, state.name)
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
        val name = nameStore.declareGenerated(region, transition, "entry_to_${transition.to.name}")
        appendIndent("contains $name: EntryTransition subsets entryTransitions") {
            appendLine("redefine refers to: State = ${transition.to.name}")
        }
    }

    private fun IndentingStringBuilder.serializeStateTransition(region: Region, transition: Transition) {
        val name = nameStore.declareGenerated(region, transition, deriveName(region, transition))
        appendIndent("contains $name: Transition subsets transitions") {
            appendLine("redefine refers from: State = ${transition.from.name}")
            appendLine("redefine refers to: State = ${transition.to.name}")

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

    private fun IndentingStringBuilder.serialize(trigger: EventTrigger) {
        appendIndent("redefine contains trigger: EventTrigger") {
            appendLine("redefine refers port: Port = ${trigger.port.name}")
            appendLine("redefine refers event: Event = ${trigger.port.name}.interface.${trigger.event.name}")
        }
    }

    private fun IndentingStringBuilder.serialize(trigger: TimeoutTrigger) {
        appendIndent("redefine contains trigger: TimeoutTrigger") {
            appendLine("redefine refers timeout: Timeout = ${trigger.timeout.name}")
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

    private fun deriveName(channel: Channel): String {
        val provided = "${channel.providedPort.instance.name}_${channel.providedPort.port.name}"
        val required = "${channel.requiredPort.instance.name}_${channel.requiredPort.port.name}"
        return "${provided}_to_$required"
    }

    private fun deriveName(region: Region, transition: Transition): String {
        val base = "${transition.from.name}_to_${transition.to.name}"
        val siblings = transitionsOf(region)
        val collides = siblings.any {
            it !== transition && "${it.from.name}_to_${it.to.name}" == base
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
        val name = nameStore.declareGenerated(action.eContainer(), action, deriveName(action, subsets))
        appendIndent("contains $name: RaiseEventAction subsets $subsets") {
            appendLine("redefine refers port: Port = ${action.port.name}")
            appendLine("redefine refers event: Event = ${action.port.name}.interface.${action.event.name}")
        }
    }

    private fun IndentingStringBuilder.serialize(
        action: SetTimeoutAction,
        subsets: String,
    ) {
        val name = nameStore.declareGenerated(action.eContainer(), action, deriveName(action, subsets))
        appendIndent("contains $name: SetTimeoutAction subsets $subsets") {
            appendLine("redefine refers timeout: Timeout = ${action.timeout.name}")
            serialize(action.value, "expression")
        }
    }

    private fun IndentingStringBuilder.serialize(
        action: AssignmentAction,
        subsets: String,
    ) {
        val name = nameStore.declareGenerated(action.eContainer(), action, deriveName(action, subsets))
        appendIndent("contains $name: AssignmentAction subsets $subsets") {
            appendLine("redefine refers variable: Variable = ${assignedVariable(action).name}")
            serialize(action.expression, "expression")
        }
    }

    private fun assignedVariable(action: AssignmentAction): VariableDeclaration {
        return (action.reference as DirectReferenceExpression).declaration as VariableDeclaration
    }

    private fun IndentingStringBuilder.serialize(verificationCase: VerificationCaseDeclaration) {
        appendLine("@VerificationCase")
        appendIndent("class ${verificationCase.name} : GammaVerificationCase") {
            val component = verificationCase.component
            val name = nameStore.declareTyped(verificationCase, component, component.name, component.component)
            appendLine("contains $name: ${component.component.name} redefines component")

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
            appendLine("redefine refers variable: Variable = ${expression.declaration.name}")
        }
    }

    private fun IndentingStringBuilder.serializeVariableReference(
        expression: VariableReferenceExpression,
        redefines: String,
    ) {
        val path = instancePath(expression) + expression.variable.name
        appendIndent("redefine contains $redefines: VariableExpression") {
            appendLine("redefine refers variable: Variable = ${path.joinToString(".")}")
        }
    }

    private fun instancePath(expression: InstanceElementExpression): List<String> {
        val verificationCase = EcoreUtil2.getContainerOfType(expression, VerificationCaseDeclaration::class.java)
        return listOf(verificationCase.component.name) + GammaUtils.getInstancePath(expression).map {
            it.name
        }
    }

    private fun containmentPath(element: EObject): List<String> {
        val path = mutableListOf<String>()
        var current = element
        while (current !is StatechartDeclaration) {
            path.add(0, (current as NamedElement).name)
            current = current.eContainer()
        }
        return path
    }

    private fun serializeInline(expression: Expression): String {
        val node = NodeModelUtils.getNode(expression)
        return NodeModelUtils.getTokenText(node)
    }
}
