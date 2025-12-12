/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.gamma.semantics

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
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.ElementReferenceExpression
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.EntryTransition
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.EventDeclaration
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.EventDirection
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.EventTrigger
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.Expression
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.GammaModelPackage
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.IntegerType
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.InterfaceDeclaration
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.IsStateActiveExpression
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.LiteralBoolean
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.LiteralExpression
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.LiteralInteger
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.NavigationSuffixExpression
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.NegationOperator
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.PortBinding
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.PortDeclaration
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.RaiseEventAction
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.RealizationMode
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.Region
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.SchedulingOrder
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.SetTimeoutAction
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.State
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.StateTransition
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.StatechartDeclaration
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.SyncComponentDeclaration
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.TimeoutDeclaration
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.TimeoutTrigger
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.Transition
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.Trigger
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.TypeReference
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.UnaryOp
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.VariableDeclaration
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.VerificationCaseDeclaration
import hu.bme.mit.semantifyr.frontends.gamma.semantics.serialization.EObjectNameProvider
import hu.bme.mit.semantifyr.frontends.gamma.semantics.serialization.IndentationAwareStringWriter
import hu.bme.mit.semantifyr.frontends.gamma.semantics.serialization.appendIndent
import hu.bme.mit.semantifyr.frontends.gamma.semantics.serialization.indent
import org.eclipse.xtext.nodemodel.util.NodeModelUtils

class GammaToOxstsSerializer {

    fun transformToOxsts(gammaPackage: GammaModelPackage) = indent {
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

    private fun IndentationAwareStringWriter.serialize(interfaceDeclaration: InterfaceDeclaration) {
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
        }
    }

    private fun IndentationAwareStringWriter.serialize(event: EventDeclaration) {
        appendLine("@Shared")
        appendLine("contains ${event.name}: Event subsets ${getEventDirection(event)}")
    }

    private fun IndentationAwareStringWriter.serialize(component: ComponentDeclaration) = when (component) {
        is SyncComponentDeclaration -> serialize(component)
        is StatechartDeclaration -> serialize(component)
        else -> error("Unknown component type: $component")
    }

    private fun IndentationAwareStringWriter.serialize(component: SyncComponentDeclaration) {
        appendIndent("class ${component.name} : SyncComponent") {
            val unboundPorts = mutableSetOf<PortDeclaration>()
            unboundPorts += component.ports
            unboundPorts -= component.bindings.map { it.port }

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

    private fun IndentationAwareStringWriter.serialize(port: PortDeclaration) {
        appendIndent("contains ${port.name}: ${getPortType(port)} subsets ports") {
            appendLine("redefine contains interface: ${port.`interface`.name}")
        }
    }

    private fun IndentationAwareStringWriter.serialize(portBinding: PortBinding) {
        appendLine("refers ${portBinding.port.name}: Port subsets ports = ${portBinding.boundPort.instance.name}.${portBinding.boundPort.port.name}")
    }

    private fun IndentationAwareStringWriter.serialize(instance: ComponentInstance) {
        appendLine("contains ${instance.name}: ${instance.component.name} subsets components")
    }

    private fun IndentationAwareStringWriter.serialize(channel: Channel) {
        appendIndent("contains ${channel.name}: Channel subsets channels") {
            appendLine("redefine refers inputPort: Port = ${channel.providedPort.instance.name}.${channel.providedPort.port.name}")
            appendLine("redefine refers outputPort: Port = ${channel.requiredPort.instance.name}.${channel.requiredPort.port.name}")
        }
    }

    private fun IndentationAwareStringWriter.serialize(statechart: StatechartDeclaration) {
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

    private fun getTypeReferenceName(typeReference: TypeReference): String {
        return when (typeReference) {
            is IntegerType -> "Integer"
            is BooleanType -> "Boolean"
            else -> error("Unknown type reference!")
        }
    }

    private fun getTypeReferenceOxstsName(typeReference: TypeReference): String {
        return when (typeReference) {
            is IntegerType -> "int"
            is BooleanType -> "bool"
            else -> error("Unknown type reference!")
        }
    }

    private fun IndentationAwareStringWriter.serialize(variable: VariableDeclaration) {
        appendIndent("contains ${variable.name}: ${getTypeReferenceName(variable.type)}Variable subsets variables") {
            if (variable.default != null) {
                val default = serializeInline(variable.default)

                appendLine("redefine refers defaultValue: ${getTypeReferenceOxstsName(variable.type)} = $default")
            }
        }
    }

    private fun IndentationAwareStringWriter.serialize(timeout: TimeoutDeclaration) {
        appendLine("contains ${timeout.name}: Timeout subsets timeouts")
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

    private fun IndentationAwareStringWriter.serialize(trigger: Trigger) = when (trigger) {
        is EventTrigger -> serialize(trigger)
        is TimeoutTrigger -> serialize(trigger)
        else -> error("Unknown trigger type: $trigger")
    }

    private fun IndentationAwareStringWriter.serialize(trigger: EventTrigger) {
        appendIndent("redefine contains trigger: EventTrigger") {
            appendLine("redefine refers port: Port = ${trigger.port.name}")
            appendLine("redefine refers event: Event = ${trigger.port.name}.interface.${trigger.event.name}")
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
            appendLine("redefine refers port: Port = ${action.port.name}")
            appendLine("redefine refers event: Event = ${action.port.name}.interface.${action.event.name}")
        }
    }

    private fun IndentationAwareStringWriter.serialize(action: SetTimeoutAction, subsets: String) {
        appendIndent("contains ${action.name}: SetTimeoutAction subsets $subsets") {
            appendLine("redefine refers timeout: Timeout = ${action.timeout.name}")
            serialize(action.value, "expression")
        }
    }

    private fun IndentationAwareStringWriter.serialize(action: AssignmentAction, subsets: String) {
        appendIndent("contains ${action.name}: AssignmentAction subsets $subsets") {
            appendLine("redefine refers variable: Variable = ${action.variable.name}")
            serialize(action.expression, "expression")
        }
    }

    private fun IndentationAwareStringWriter.serialize(verificationCase: VerificationCaseDeclaration) {
        if (verificationCase.isReachable) {
            appendLine("@VerificationCase(VerificationResult::UNSAFE)")
        } else {
            appendLine("@VerificationCase(VerificationResult::SAFE)")
        }
        appendIndent("class ${verificationCase.name} : GammaVerificationCase") {
            appendLine("contains ${verificationCase.component.name}: ${verificationCase.component.component.name} redefines component")

            serialize(verificationCase.invariant, "propertyExpression")
        }
    }

    private fun IndentationAwareStringWriter.serialize(expression: Expression, redefines: String) = when (expression) {
        is ArithmeticBinaryOperator -> serialize(expression, redefines)
        is ArithmeticUnaryOperator -> serialize(expression, redefines)
        is BooleanOperator -> serialize(expression, redefines)
        is ComparisonOperator -> serialize(expression, redefines)
        is LiteralExpression -> serialize(expression, redefines)
        is NegationOperator -> serialize(expression, redefines)
        is IsStateActiveExpression -> serialize(expression, redefines)
        is ElementReferenceExpression -> serializeVariableReference(expression, redefines)
        is NavigationSuffixExpression -> serializeVariableReference(expression, redefines)
        else -> error("Unknown type of expression: $expression")
    }

    private fun getOpName(op: ArithmeticOp): String {
        return when (op) {
            ArithmeticOp.ADD -> "PlusOperatorExpression"
            ArithmeticOp.SUB -> "MinusOperatorExpression"
            ArithmeticOp.MUL -> "MultiplyOperatorExpression"
            ArithmeticOp.DIV -> "DivideOperatorExpression"
        }
    }

    private fun getOpName(op: UnaryOp): String {
        return when (op) {
            UnaryOp.PLUS -> "UnaryPlusExpression"
            UnaryOp.MINUS -> "UnaryMinusExpression"
        }
    }

    private fun getOpName(op: BooleanOp): String {
        return when (op) {
            BooleanOp.AND -> "AndOperatorExpression"
            BooleanOp.OR -> "OrOperatorExpression"
        }
    }

    private fun getOpName(op: ComparisonOp): String {
        return when (op) {
            ComparisonOp.LESS -> "LessThanOperatorExpression"
            ComparisonOp.LESS_EQ -> "LessThanOrEqualsOperatorExpression"
            ComparisonOp.GREATER -> "GreaterThanOperatorExpression"
            ComparisonOp.GREATER_EQ -> "GreaterThanOrEqualsOperatorExpression"
            ComparisonOp.EQ -> "EqualityOperatorExpression"
            ComparisonOp.NOT_EQ -> "InequalityOperatorExpression"
        }
    }

    private fun IndentationAwareStringWriter.serialize(expression: ArithmeticBinaryOperator, redefines: String) {
        serializeOperator(expression.left, expression.right, getOpName(expression.op), redefines)
    }

    private fun IndentationAwareStringWriter.serialize(expression: ArithmeticUnaryOperator, redefines: String) {
        serializeOperator(expression.body, getOpName(expression.op), redefines)
    }

    private fun IndentationAwareStringWriter.serialize(expression: BooleanOperator, redefines: String) {
        serializeOperator(expression.left, expression.right, getOpName(expression.op), redefines)
    }

    private fun IndentationAwareStringWriter.serialize(expression: ComparisonOperator, redefines: String) {
        serializeOperator(expression.left, expression.right, getOpName(expression.op), redefines)
    }

    private fun IndentationAwareStringWriter.serializeOperator(left: Expression, right: Expression, name: String, redefines: String) {
        appendIndent("redefine contains $redefines: $name") {
            serialize(left, "left")
            serialize(right, "right")
        }
    }

    private fun IndentationAwareStringWriter.serializeOperator(body: Expression, name: String, redefines: String) {
        appendIndent("redefine contains $redefines: $name") {
            serialize(body, "body")
        }
    }

    private fun IndentationAwareStringWriter.serialize(expression: LiteralExpression, redefines: String) {
        when (expression) {
            is LiteralInteger -> serialize(expression, redefines)
            is LiteralBoolean -> serialize(expression, redefines)
            else -> error("")
        }
    }

    private fun IndentationAwareStringWriter.serialize(expression: LiteralInteger, redefines: String) {
        appendIndent("redefine contains $redefines: LiteralIntegerExpression") {
            appendLine("redefine refers value: int = ${expression.value}")
        }
    }

    private fun IndentationAwareStringWriter.serialize(expression: LiteralBoolean, redefines: String) {
        appendIndent("redefine contains $redefines: LiteralBooleanExpression") {
            appendLine("redefine refers value: bool = ${expression.isValue}")
        }
    }

    private fun IndentationAwareStringWriter.serialize(expression: NegationOperator, redefines: String) {
        serializeOperator(expression.body, "UnaryNotExpression", redefines)
    }

    private fun IndentationAwareStringWriter.serialize(expression: IsStateActiveExpression, redefines: String) {
        appendIndent("redefine contains $redefines: IsStateActiveExpression") {
            appendLine("redefine refers state: State = ${serializeInline(expression.expression)}")
        }
    }

    private fun IndentationAwareStringWriter.serializeVariableReference(expression: ElementReferenceExpression, redefines: String) {
        appendIndent("redefine contains $redefines: VariableExpression") {
            appendLine("redefine refers variable: Variable = ${serializeInline(expression)}")
        }
    }

    private fun IndentationAwareStringWriter.serializeVariableReference(expression: NavigationSuffixExpression, redefines: String) {
        appendIndent("redefine contains $redefines: VariableExpression") {
            appendLine("redefine refers variable: Variable = ${serializeInline(expression)}")
        }
    }

    private fun serializeInline(expression: Expression): String {
        val node = NodeModelUtils.getNode(expression);
        return NodeModelUtils.getTokenText(node)
    }

    private val actionNameProvider = EObjectNameProvider("action")
    private val Action.name by actionNameProvider

    private val transitionNameProvider = EObjectNameProvider("transition")
    private val Transition.name by transitionNameProvider

    private val channelNameProvider = EObjectNameProvider("channel")
    private val Channel.name by channelNameProvider

}
