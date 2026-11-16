/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.gamma.trace

import com.google.inject.Inject
import com.google.inject.Provider
import hu.bme.mit.semantifyr.backend.witness.Fraction
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.Act
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.AsynchronousAdapterDeclaration
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.BooleanType
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.ComparisonOp
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.ComponentInstance
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.ComponentInstanceReference
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.CompositeComponentDeclaration
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.EventDeclaration
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.ExecutionTrace
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.Expression
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.GammaFactory
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.GammaModelPackage
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.GammaUtils
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.PortDeclaration
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.Region
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.State
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.Step
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.Transition
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.UnaryOp
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.VariableDeclaration
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.VerificationCaseDeclaration
import hu.bme.mit.semantifyr.frontends.gamma.serialization.GammaNameMap
import hu.bme.mit.semantifyr.verifier.ReportCall
import hu.bme.mit.semantifyr.verifier.ReportState
import hu.bme.mit.semantifyr.verifier.ReportStep
import hu.bme.mit.semantifyr.verifier.ReportTrace
import hu.bme.mit.semantifyr.verifier.ReportValue
import org.eclipse.emf.common.util.URI
import org.eclipse.emf.ecore.EObject
import org.eclipse.emf.ecore.util.EcoreUtil
import org.eclipse.xtext.resource.IResourceDescription
import org.eclipse.xtext.resource.IResourceFactory
import org.eclipse.xtext.resource.XtextResourceSet
import org.eclipse.xtext.resource.impl.ResourceDescriptionsData

class GammaTraceGenerator @Inject constructor(
    private val resourceSetProvider: Provider<XtextResourceSet>,
    private val resourceFactory: IResourceFactory,
    private val resourceDescriptionManager: IResourceDescription.Manager,
) {

    private val factory = GammaFactory.eINSTANCE

    fun generate(
        verificationCase: VerificationCaseDeclaration,
        trace: ReportTrace,
        nameMap: GammaNameMap,
        timeUnitMilliseconds: Int,
    ): ExecutionTrace {
        val paths = InstancePaths(verificationCase, nameMap)

        val executionTrace = factory.createExecutionTrace().also {
            it.imports += factory.createImportStatement().also { importStatement ->
                importStatement.importedPackage = verificationCase.eContainer() as GammaModelPackage
            }
            it.name = "${verificationCase.name}Trace"
            it.verificationCase = verificationCase
            it.steps += createInitializationStep(trace.initial.calls, trace.states[trace.initial.state], paths)
            for (step in trace.steps) {
                it.steps += createStep(step, trace.states[step.target], paths, timeUnitMilliseconds)
            }
        }

        addToResource(executionTrace, verificationCase)
        return executionTrace
    }

    private fun addToResource(executionTrace: ExecutionTrace, verificationCase: VerificationCaseDeclaration) {
        val resourceSet = resourceSetProvider.get()
        val modelDescription = resourceDescriptionManager.getResourceDescription(verificationCase.eResource())
        ResourceDescriptionsData.ResourceSetAdapter.installResourceDescriptionsData(resourceSet, ResourceDescriptionsData(listOf(modelDescription)))
        val resource = resourceFactory.createResource(URI.createURI(TRACE_RESOURCE_NAME))
        resourceSet.resources += resource
        resource.contents += executionTrace
    }

    private fun createInitializationStep(
        calls: List<ReportCall>,
        state: ReportState,
        paths: InstancePaths,
    ): Step {
        return factory.createStep().also {
            it.acts += factory.createResetAct()
            it.asserts += createTraceAssertions(calls, paths)
            it.asserts += createStateAssertions(state, paths)
        }
    }

    private fun createStep(
        reportStep: ReportStep,
        state: ReportState,
        paths: InstancePaths,
        timeUnitMilliseconds: Int,
    ): Step {
        return factory.createStep().also { step ->
            step.acts += flatten(reportStep.calls).mapNotNull {
                createInputAct(it, paths)
            }
            if (reportStep.elapsed != Fraction.ZERO) {
                step.acts += createTimeElapseAct(reportStep.elapsed, timeUnitMilliseconds)
            }
            step.acts += factory.createScheduleAct()
            step.asserts += createTraceAssertions(reportStep.calls, paths)
            step.asserts += createStateAssertions(state, paths)
        }
    }

    private fun createTimeElapseAct(elapsed: Fraction, timeUnitMilliseconds: Int): Act {
        val milliseconds = Fraction.of(elapsed.numerator * timeUnitMilliseconds.toBigInteger(), elapsed.denominator)
        return factory.createTimeElapseAct().also {
            it.time = milliseconds.numerator.intValueExact()
            it.denominator = milliseconds.denominator.intValueExact()
        }
    }

    private fun createInputAct(call: ReportCall, paths: InstancePaths): Act? {
        if (call.transition !in INPUT_CALLS) {
            return null
        }
        val receiver = paths.resolve(call.self)
        val port = paths.tracedPort(receiver) ?: error("Input on '${call.self}' does not reach a port of the traced component")
        return factory.createRaiseEventAct().also {
            it.port = port
            it.event = resolveEvent(call, paths)
        }
    }

    private fun createTraceAssertions(calls: List<ReportCall>, paths: InstancePaths): List<Expression> {
        return flatten(calls).mapNotNull {
            createTraceAssertion(it, paths)
        }
    }

    private fun createTraceAssertion(call: ReportCall, paths: InstancePaths): Expression? {
        val receiver = paths.resolve(call.self)
        return when (val element = receiver.lastOrNull()) {
            null -> null
            is Transition -> createFiredTransitionAssertion(receiver, element)
            is PortDeclaration -> {
                when (call.transition) {
                    in INPUT_CALLS -> null
                    SET_OUTPUT_EVENT -> {
                        factory.createRaiseEventAct().also {
                            it.instance = createInstanceReference(receiver)
                            it.port = element
                            it.event = resolveEvent(call, paths)
                        }
                    }
                    else -> error("Traced call '${call.transition}' on port '${call.self}' is not an event raise")
                }
            }
            else -> error("Traced call on '${call.self}' is not a transition firing or an event raise")
        }
    }

    // a transition into a history node without an id has no fired-transition form, the asserted states show where it led
    private fun createFiredTransitionAssertion(receiver: List<EObject>, transition: Transition): Expression? {
        val from = transition.from as State
        val to = transition.to
        if (transition.name == null && to !is State) {
            return null
        }
        return factory.createFiredTransitionExpression().also {
            it.instance = createInstanceReference(receiver)
            if (transition.name != null) {
                it.transition = transition
                return@also
            }
            it.from = from
            it.to = to as State
            if (GammaUtils.findTransitions(from, to, null).size > 1) {
                it.trigger = EcoreUtil.copy(transition.trigger)
            }
        }
    }

    private fun resolveEvent(call: ReportCall, paths: InstancePaths): EventDeclaration {
        val path = (call.arguments.values.single() as? ReportValue.Text)?.value ?: error("Traced event of '${call.self}' is no instance")
        return paths.resolve(path).last() as? EventDeclaration ?: error("Traced event '$path' is not a Gamma event")
    }

    private fun createStateAssertions(state: ReportState, paths: InstancePaths): List<Expression> {
        val activeStates = mutableListOf<List<EObject>>()
        val variableValues = mutableListOf<VariableValue>()
        for ((variable, value) in state.values) {
            val holder = paths.resolveOrNull(variable.substringBeforeLast('.')) ?: continue
            when (holder.lastOrNull()) {
                is Region -> {
                    (value as? ReportValue.Text)?.let {
                        paths.resolveOrNull(it.value)
                    }?.let {
                        activeStates += it
                    }
                }
                is VariableDeclaration -> {
                    // a record value has no assertion form in the trace yet
                    if (value !is ReportValue.Record) {
                        variableValues += VariableValue(holder, value)
                    }
                }
            }
        }

        val stateAssertions = activeStates.sortedWith(MODEL_ORDER).map { path ->
            val state = path.last() as State
            factory.createStateReferenceExpression().also {
                it.instance = createInstanceReference(path)
                it.region = state.eContainer() as Region
                it.state = state
            }
        }
        val variableAssertions = variableValues.sortedWith(compareBy(MODEL_ORDER) { it.path }).map { variableValue ->
            val variable = variableValue.path.last() as VariableDeclaration
            factory.createComparisonOperator().also {
                it.left = factory.createVariableReferenceExpression().also { reference ->
                    reference.instance = createInstanceReference(variableValue.path)
                    reference.variable = variable
                }
                it.op = ComparisonOp.EQ
                it.right = createValue(variable, variableValue.value)
            }
        }
        return stateAssertions + variableAssertions
    }

    private class VariableValue(
        val path: List<EObject>,
        val value: ReportValue,
    )

    // a path starts at the case's component, in whose scope a trace names everything
    private fun createInstanceReference(path: List<EObject>): ComponentInstanceReference? {
        val instances = path.drop(1).takeWhile {
            it is ComponentInstance
        }.filterIsInstance<ComponentInstance>()
        return instances.foldRight(null as ComponentInstanceReference?) { instance, child ->
            factory.createComponentInstanceReference().also {
                it.instance = instance
                it.child = child
            }
        }
    }

    private fun createValue(variable: VariableDeclaration, value: ReportValue): Expression {
        if (variable.type is BooleanType) {
            return factory.createLiteralBoolean().also {
                it.isValue = (value as? ReportValue.Bool)?.value ?: error("Variable '${variable.name}' holds $value, not a boolean")
            }
        }
        val number = (value as? ReportValue.Integer)?.value?.intValueExact() ?: error("Variable '${variable.name}' holds $value, not an integer")
        val literal = factory.createLiteralInteger().also {
            it.value = Math.absExact(number)
        }
        if (number >= 0) {
            return literal
        }
        return factory.createArithmeticUnaryOperator().also {
            it.op = UnaryOp.MINUS
            it.body = literal
        }
    }

    private fun flatten(calls: List<ReportCall>): List<ReportCall> {
        return calls.flatMap {
            listOf(it) + flatten(it.calls)
        }
    }

    private class InstancePaths(
        private val verificationCase: VerificationCaseDeclaration,
        private val nameMap: GammaNameMap,
    ) {
        fun resolveOrNull(path: String): List<EObject>? {
            val names = path.split('.')
            if (names.first() != ROOT_FEATURE) {
                return null
            }
            return nameMap.resolveOrNull(verificationCase, names.drop(1))
        }

        fun resolve(path: String): List<EObject> {
            return resolveOrNull(path) ?: error("Instance path '$path' has no Gamma element")
        }

        fun tracedPort(path: List<EObject>): PortDeclaration? {
            val instances = path.filterIsInstance<ComponentInstance>()
            var port = path.last() as PortDeclaration
            for (index in instances.lastIndex downTo 0) {
                val instance = instances[index]
                val container = if (index == 0) {
                    path.first()
                } else {
                    instances[index - 1].component
                }
                // an adapter offers its wrapped component's ports as its own
                if (container is AsynchronousAdapterDeclaration && container.wrappedComponent == instance) {
                    continue
                }
                val composite = container as? CompositeComponentDeclaration ?: return null
                val binding = composite.bindings.firstOrNull {
                    it.boundPort.instance == instance && it.boundPort.port == port
                } ?: return null
                port = binding.port
            }
            return port
        }
    }

    companion object {
        private const val ROOT_FEATURE = "root"
        private const val TRACE_RESOURCE_NAME = "trace.get"
        private const val RAISE_INPUT_EVENT = "semantifyr::gamma::ports::Port::raiseInputEvent"
        private const val SET_OUTPUT_EVENT = "semantifyr::gamma::ports::Port::setOutputEvent"
        private const val RECEIVE_INPUT_MESSAGE = "semantifyr::gamma::ports::Port::receiveInputMessage"
        private val INPUT_CALLS = setOf(
            RAISE_INPUT_EVENT,
            RECEIVE_INPUT_MESSAGE,
        )

        private val MODEL_ORDER = Comparator<List<EObject>> { left, right ->
            val leftIndices = left.map {
                it.eContainer().eContents().indexOf(it)
            }
            val rightIndices = right.map {
                it.eContainer().eContents().indexOf(it)
            }
            val firstDifference = leftIndices.zip(rightIndices).firstOrNull {
                it.first != it.second
            }
            firstDifference?.let {
                it.first.compareTo(it.second)
            } ?: leftIndices.size.compareTo(rightIndices.size)
        }
    }
}
