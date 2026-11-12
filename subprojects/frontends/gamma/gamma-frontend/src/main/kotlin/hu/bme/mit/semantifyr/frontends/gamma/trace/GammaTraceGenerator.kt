/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.gamma.trace

import com.google.inject.Inject
import com.google.inject.Provider
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.Act
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
import hu.bme.mit.semantifyr.frontends.gamma.serialization.GammaNameStore
import hu.bme.mit.semantifyr.verifier.witness.CallTrace
import hu.bme.mit.semantifyr.verifier.witness.CallTraceStep
import hu.bme.mit.semantifyr.verifier.witness.TraceEntry
import hu.bme.mit.semantifyr.verifier.witness.WitnessState
import hu.bme.mit.semantifyr.verifier.witness.WitnessStateStep
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
        callTrace: CallTrace,
        witnessState: WitnessState,
        nameStore: GammaNameStore,
    ): ExecutionTrace {
        require(callTrace.steps.size == witnessState.steps.size) {
            "Trace has ${callTrace.steps.size} steps but the witness has ${witnessState.steps.size}"
        }
        val paths = InstancePaths(verificationCase, nameStore)

        val executionTrace = factory.createExecutionTrace().also {
            it.imports += factory.createImportStatement().also { importStatement ->
                importStatement.importedPackage = verificationCase.eContainer() as GammaModelPackage
            }
            it.name = "${verificationCase.name}Trace"
            it.verificationCase = verificationCase
            it.steps += createInitializationStep(callTrace.initializedStep, witnessState.initializedStep, paths)
            for ((index, step) in callTrace.steps.withIndex()) {
                it.steps += createStep(step, witnessState.steps[index], paths)
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
        callTraceStep: CallTraceStep,
        witnessStateStep: WitnessStateStep,
        paths: InstancePaths,
    ): Step {
        return factory.createStep().also {
            it.acts += factory.createResetAct()
            it.asserts += createTraceAssertions(callTraceStep, paths)
            it.asserts += createStateAssertions(witnessStateStep, paths)
        }
    }

    private fun createStep(
        callTraceStep: CallTraceStep,
        witnessStateStep: WitnessStateStep,
        paths: InstancePaths,
    ): Step {
        return factory.createStep().also { step ->
            step.acts += flatten(callTraceStep.traces).mapNotNull {
                createInputAct(it, paths)
            }
            if (advancesTime(callTraceStep, paths)) {
                step.acts += factory.createTimeElapseAct().also {
                    it.time = TIME_ADVANCE_MILLISECONDS
                }
            }
            step.acts += factory.createScheduleAct()
            step.asserts += createTraceAssertions(callTraceStep, paths)
            step.asserts += createStateAssertions(witnessStateStep, paths)
        }
    }

    private fun advancesTime(callTraceStep: CallTraceStep, paths: InstancePaths): Boolean {
        return flatten(callTraceStep.traces).any {
            paths.resolve(it.self).isEmpty()
        }
    }

    private fun createInputAct(entry: TraceEntry, paths: InstancePaths): Act? {
        if (entry.calledTransition != RAISE_INPUT_EVENT) {
            return null
        }
        val receiver = paths.resolve(entry.self)
        val port = paths.tracedPort(receiver) ?: error("Input on '${entry.self}' does not reach a port of the traced component")
        return factory.createRaiseEventAct().also {
            it.port = port
            it.event = resolveEvent(entry, paths)
        }
    }

    private fun createTraceAssertions(callTraceStep: CallTraceStep, paths: InstancePaths): List<Expression> {
        return flatten(callTraceStep.traces).mapNotNull {
            createTraceAssertion(it, paths)
        }
    }

    private fun createTraceAssertion(entry: TraceEntry, paths: InstancePaths): Expression? {
        val receiver = paths.resolve(entry.self)
        return when (val element = receiver.lastOrNull()) {
            null -> null
            is Transition -> createFiredTransitionAssertion(receiver, element)
            is PortDeclaration -> {
                when (entry.calledTransition) {
                    RAISE_INPUT_EVENT -> null
                    SET_OUTPUT_EVENT -> {
                        paths.tracedPort(receiver)?.let { port ->
                            factory.createRaiseEventAct().also {
                                it.port = port
                                it.event = resolveEvent(entry, paths)
                            }
                        }
                    }
                    else -> error("Traced call '${entry.calledTransition}' on port '${entry.self}' is not an event raise")
                }
            }
            else -> error("Traced call on '${entry.self}' is not a transition firing, an event raise or the time advance")
        }
    }

    private fun createFiredTransitionAssertion(receiver: List<EObject>, transition: Transition): Expression {
        return factory.createFiredTransitionExpression().also {
            it.instance = createInstanceReference(receiver)
            if (transition.name != null) {
                it.transition = transition
                return@also
            }
            val from = transition.from as State
            val to = transition.to as State
            it.from = from
            it.to = to
            if (GammaUtils.findTransitions(from, to, null).size > 1) {
                it.trigger = EcoreUtil.copy(transition.trigger)
            }
        }
    }

    private fun resolveEvent(entry: TraceEntry, paths: InstancePaths): EventDeclaration {
        val argument = entry.arguments.single()
        return paths.resolve(argument.value).last() as? EventDeclaration ?: error("Traced event '${argument.value}' is not a Gamma event")
    }

    private fun createStateAssertions(witnessStateStep: WitnessStateStep, paths: InstancePaths): List<Expression> {
        val activeStates = mutableListOf<List<EObject>>()
        val variableValues = mutableListOf<VariableValue>()
        for (value in witnessStateStep.values) {
            val holder = paths.resolveOrNull(value.variable.substringBeforeLast('.')) ?: continue
            when (holder.lastOrNull()) {
                is Region -> {
                    paths.resolveOrNull(value.value)?.let {
                        activeStates += it
                    }
                }
                is VariableDeclaration -> {
                    variableValues += VariableValue(holder, value.value)
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
        val value: String,
    )

    // the first instance of a path is the traced case's own, which a trace names as the component it traces
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

    private fun createValue(variable: VariableDeclaration, value: String): Expression {
        if (variable.type is BooleanType) {
            return factory.createLiteralBoolean().also {
                it.isValue = value.toBooleanStrict()
            }
        }
        val number = value.filterNot {
            it.isWhitespace()
        }.toInt()
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

    private fun flatten(entries: List<TraceEntry>): List<TraceEntry> {
        return entries.flatMap {
            listOf(it) + flatten(it.innerTraces.orEmpty())
        }
    }

    private class InstancePaths(
        private val verificationCase: VerificationCaseDeclaration,
        private val nameStore: GammaNameStore,
    ) {
        fun resolveOrNull(path: String): List<EObject>? {
            val names = path.split('.')
            if (names.first() != ROOT_FEATURE) {
                return null
            }
            return nameStore.resolveOrNull(verificationCase, names.drop(1))
        }

        fun resolve(path: String): List<EObject> {
            return resolveOrNull(path) ?: error("Instance path '$path' has no Gamma element")
        }

        // a port of an inner instance is named on the traced component through the bindings that reach it
        fun tracedPort(path: List<EObject>): PortDeclaration? {
            val instances = path.dropLast(1).filterIsInstance<ComponentInstance>()
            var port = path.last() as PortDeclaration
            for (index in instances.lastIndex downTo 1) {
                val instance = instances[index]
                val composite = instances[index - 1].component as? CompositeComponentDeclaration ?: return null
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
        private const val TIME_ADVANCE_MILLISECONDS = 1000
        private const val TRACE_RESOURCE_NAME = "trace.get"
        private const val RAISE_INPUT_EVENT = "semantifyr::gamma::ports::Port::raiseInputEvent"
        private const val SET_OUTPUT_EVENT = "semantifyr::gamma::ports::Port::setOutputEvent"

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
