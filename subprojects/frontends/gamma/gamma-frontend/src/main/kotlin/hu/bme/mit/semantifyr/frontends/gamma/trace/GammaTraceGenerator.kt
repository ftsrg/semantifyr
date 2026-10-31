/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.gamma.trace

import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.Act
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.Assertion
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.BooleanType
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.EventDeclaration
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.Expression
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.GammaFactory
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.GammaModelPackage
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.GammaUtils
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.PortDeclaration
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.Region
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.StateTransition
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.Step
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.UnaryOp
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.VariableDeclaration
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.VerificationCaseDeclaration
import hu.bme.mit.semantifyr.frontends.gamma.serialization.GammaNameStore
import hu.bme.mit.semantifyr.verifier.witness.CallTrace
import hu.bme.mit.semantifyr.verifier.witness.CallTraceStep
import hu.bme.mit.semantifyr.verifier.witness.TraceEntry
import hu.bme.mit.semantifyr.verifier.witness.WitnessState
import hu.bme.mit.semantifyr.verifier.witness.WitnessStateStep
import org.eclipse.emf.ecore.EObject
import org.eclipse.emf.ecore.util.EcoreUtil

class GammaTraceGenerator {

    private val factory = GammaFactory.eINSTANCE

    fun generate(
        verificationCase: VerificationCaseDeclaration,
        callTrace: CallTrace,
        witnessState: WitnessState,
        nameStore: GammaNameStore,
    ): GammaModelPackage {
        require(callTrace.steps.size == witnessState.steps.size) {
            "Trace has ${callTrace.steps.size} steps but the witness has ${witnessState.steps.size}"
        }
        val paths = InstancePaths(verificationCase, nameStore)

        val executionTrace = factory.createExecutionTrace().also {
            it.name = "${verificationCase.name}Trace"
            it.verificationCase = verificationCase
            it.steps += createInitializationStep(callTrace.initializedStep, witnessState.initializedStep, paths)
            for ((index, step) in callTrace.steps.withIndex()) {
                it.steps += createStep(step, witnessState.steps[index], paths)
            }
        }

        return factory.createGammaModelPackage().also {
            it.name = (verificationCase.eContainer() as GammaModelPackage).name
            it.traces += executionTrace
        }
    }

    private fun createInitializationStep(
        callTraceStep: CallTraceStep?,
        witnessStateStep: WitnessStateStep?,
        paths: InstancePaths,
    ): Step {
        return factory.createStep().also {
            it.acts += factory.createResetAct()
            if (callTraceStep != null) {
                it.asserts += createTraceAssertions(callTraceStep, paths)
            }
            if (witnessStateStep != null) {
                it.asserts += createStateAssertions(witnessStateStep, paths)
            }
        }
    }

    private fun createStep(
        callTraceStep: CallTraceStep,
        witnessStateStep: WitnessStateStep,
        paths: InstancePaths,
    ): Step {
        val entries = flatten(callTraceStep.traces)
        val advancesTime = entries.any {
            paths.resolve(it.self).isEmpty()
        }
        return factory.createStep().also { step ->
            step.acts += entries.mapNotNull {
                createInputAct(it, paths)
            }
            step.acts += factory.createScheduleAct()
            if (advancesTime) {
                step.acts += factory.createTimeElapseAct().also {
                    it.time = TIME_ADVANCE_SECONDS
                }
            }
            step.asserts += createTraceAssertions(callTraceStep, paths)
            step.asserts += createStateAssertions(witnessStateStep, paths)
        }
    }

    private fun createInputAct(entry: TraceEntry, paths: InstancePaths): Act? {
        val receiver = paths.resolve(entry.self)
        val port = receiver.lastOrNull() as? PortDeclaration ?: return null
        val event = resolveEvent(entry, paths)
        if (!GammaUtils.isInputEvent(port, event)) {
            return null
        }
        return factory.createRaiseEventAct().also {
            it.event = createPath(receiver + event)
        }
    }

    private fun createTraceAssertions(callTraceStep: CallTraceStep, paths: InstancePaths): List<Assertion> {
        return flatten(callTraceStep.traces).mapNotNull {
            createTraceAssertion(it, paths)
        }
    }

    private fun createTraceAssertion(entry: TraceEntry, paths: InstancePaths): Assertion? {
        val receiver = paths.resolve(entry.self)
        return when (val element = receiver.lastOrNull()) {
            null -> null
            is StateTransition -> createFiredTransitionAssertion(receiver.dropLast(1), element)
            is PortDeclaration -> {
                val event = resolveEvent(entry, paths)
                if (GammaUtils.isInputEvent(element, event)) {
                    null
                } else {
                    factory.createRaisedEventAssertion().also {
                        it.event = createPath(receiver + event)
                    }
                }
            }
            else -> error("Traced call on '${entry.self}' is not a transition firing, an event raise or the time advance")
        }
    }

    private fun createFiredTransitionAssertion(regionPath: List<EObject>, transition: StateTransition): Assertion {
        return factory.createFiredTransitionAssertion().also {
            it.from = createPath(regionPath + transition.from)
            it.to = transition.to
            if (GammaUtils.findTransitions(transition.from, transition.to, null).size > 1) {
                it.trigger = EcoreUtil.copy(transition.trigger)
            }
        }
    }

    private fun resolveEvent(entry: TraceEntry, paths: InstancePaths): EventDeclaration {
        val argument = entry.arguments.single()
        return paths.resolve(argument.value).last() as? EventDeclaration ?: error("Traced event '${argument.value}' is not a Gamma event")
    }

    private fun createStateAssertions(witnessStateStep: WitnessStateStep, paths: InstancePaths): List<Assertion> {
        val activeStates = mutableListOf<List<EObject>>()
        val variableValues = mutableListOf<Pair<List<EObject>, String>>()
        for (value in witnessStateStep.values) {
            val holder = paths.resolveOrNull(value.variable.substringBeforeLast('.')) ?: continue
            when (holder.lastOrNull()) {
                is Region -> {
                    paths.resolveOrNull(value.value)?.let {
                        activeStates += it
                    }
                }
                is VariableDeclaration -> {
                    variableValues += holder to value.value
                }
            }
        }

        val leafStates = activeStates.filter { state ->
            activeStates.none {
                it.size > state.size && it.subList(0, state.size) == state
            }
        }

        val stateAssertions = leafStates.sortedWith(MODEL_ORDER).map { state ->
            factory.createActiveStateAssertion().also {
                it.state = createPath(state)
            }
        }
        val variableAssertions = variableValues.sortedWith(compareBy(MODEL_ORDER) { it.first }).map { (variable, value) ->
            factory.createVariableValueAssertion().also {
                it.variable = createPath(variable)
                it.value = createValue(variable.last() as VariableDeclaration, value)
            }
        }
        return stateAssertions + variableAssertions
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

    private fun createPath(elements: List<EObject>): Expression {
        val root: Expression = factory.createElementReferenceExpression().also {
            it.element = elements.first()
        }
        return elements.drop(1).fold(root) { primary, member ->
            factory.createNavigationSuffixExpression().also {
                it.primary = primary
                it.member = member
            }
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
    }

    companion object {
        private const val ROOT_FEATURE = "root"
        private const val TIME_ADVANCE_SECONDS = 1

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
