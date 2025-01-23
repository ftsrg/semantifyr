/*
 * SPDX-FileCopyrightText: 2023-2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.semantifyr.theta

import hu.bme.mit.semantifyr.cex.lang.cex.Cex
import hu.bme.mit.semantifyr.cex.lang.cex.ExplState
import hu.bme.mit.semantifyr.cex.lang.cex.ExplVariableState
import hu.bme.mit.semantifyr.cex.lang.cex.ExplVariableValue
import hu.bme.mit.semantifyr.cex.lang.cex.LiteralBoolean
import hu.bme.mit.semantifyr.cex.lang.cex.LiteralEnum
import hu.bme.mit.semantifyr.cex.lang.cex.LiteralInteger
import hu.bme.mit.semantifyr.cex.lang.cex.State
import hu.bme.mit.semantifyr.cex.lang.cex.XstsState
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AssignmentOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AssumptionOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ChoiceOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.SequenceOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Target
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Transition
import hu.bme.mit.semantifyr.oxsts.semantifyr.reader.OxstsReader
import hu.bme.mit.semantifyr.oxsts.semantifyr.transformation.optimization.OperationOptimizer.optimize
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.MultiMap
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.Namings
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.NothingInstance
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.OxstsFactory
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils._package
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.copy
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.findInitTransition
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.findMainTransition
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.fullyQualifiedName
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.resolvePath

fun ExplVariableValue.toExpression(target: Target): Expression {
    return when (this) {
        is LiteralEnum -> toExpression(target)
        is LiteralBoolean -> OxstsFactory.createLiteralBoolean(isValue)
        is LiteralInteger -> OxstsFactory.createLiteralInteger(value)
        else -> error("Unkown VariableValue: $this")
    }
}

fun LiteralEnum.toExpression(target: Target): Expression {
    val instanceFullyQualifiedName = value.removeSuffix(Namings.LITERAL_SUFFIX)

    if (instanceFullyQualifiedName == NothingInstance.fullyQualifiedName) {
        return OxstsFactory.createChainReferenceExpression(OxstsFactory.createNothingReference())
    }

    return target.resolvePath(instanceFullyQualifiedName)
}

class OxstsVariableState(
    variableState: ExplVariableState,
    target: Target
) {
    val reference = target.resolvePath(variableState.variable)
    val value = variableState.value.toExpression(target)
}
fun ExplVariableState.toOxstsVariableState(target: Target) = OxstsVariableState(this, target)

fun OxstsVariableState.toAssumption(): AssumptionOperation {
    return OxstsFactory.createEqualityAssumption(reference.copy(), value.copy())
}

class OxstsWitnessState(
    state: ExplState,
    target: Target
) {
    val id = maxId++
    val variableStates = state.variableStates.map {
        it.toOxstsVariableState(target)
    }

    companion object {
        private var maxId = 0;
    }
}
fun ExplState.toOxstsWitnessState(target: Target) = OxstsWitnessState(this, target)

fun OxstsWitnessState.toAssumptionOperations(): List<AssumptionOperation> {
    return variableStates.map {
        it.toAssumption()
    }
}

val XstsState.isTran
    get() = isPostInit && isLastInternal

class Witness(
    cex: Cex,
    target: Target
) {

    private val stateMap = mutableMapOf<State, OxstsWitnessState>()

//    val initialState = cex.states.single {
//        it.isPreInit
//    }.state.toOxstsWitnessState(target)

    val initializedState = cex.states.firstOrNull {
        it.isTran
    }?.state?.transform(target)

    val transitionStates = cex.states.filter {
        it.isTran
    }.drop(1).map { // first state is the init transition
        it.state.transform(target)
    }

    private val edgeMap = MultiMap<State, State>().also {
        for (edge in cex.edges) {
            it.put(edge.from.state, edge.to.state)
        }
    }

    val isSingleTrace = cex.edges.isEmpty()
    val isSummary = !isSingleTrace

    val transitions = MultiMap<OxstsWitnessState, OxstsWitnessState>().also {
        val tranEdges = cex.edges.filter {
            it.from.isTran
        }

        if (tranEdges.isEmpty()) {
            if (transitionStates.any()) {
                if (initializedState != null) {
                    it.put(initializedState, transitionStates.first())
                }
                for (index in 1..<transitionStates.size) {
                    val sourceState = transitionStates[index - 1]
                    val targetState = transitionStates[index]

                    it.put(sourceState, targetState)
                }
            }
        } else {
            for (edge in tranEdges) {
                val sourceState = edge.from.state.transform(target)
                val environmentTarget = edge.to.state
                val tranTargets = edgeMap[environmentTarget].map {
                    it.transform(target)
                }

                it.putAll(sourceState, tranTargets)
            }
        }
    }

    fun State.transform(target: Target) = stateMap.getOrPut(this) {
        when (this) {
            is ExplState -> toOxstsWitnessState(target)
            else -> error("Unknown State $this")
        }
    }

}

fun Cex.toWitness(target: Target) = Witness(this, target)

class OxstsWitness(
    private val target: Target,
    cex: Cex
) {

    val witnessPackage by lazy {
        OxstsFactory.createPackage().also {
            it.name = target._package.name
        }
    }

    private val witnessType by lazy {
        OxstsFactory.createTarget().also {
            witnessPackage.types += it

            it.name = "${target.name}_Witness"
            it.supertype = target
        }
    }

    private val stateVariable by lazy {
        OxstsFactory.createVariable().also {
            witnessType.variables += it

            it.name = "state"
            it.typing = OxstsFactory.createIntegerType()
        }
    }

    init {
        val witness = cex.toWitness(target)

        witnessType.initTransition += OxstsFactory.createSequentialTransition {
            if (witness.initializedState != null) {
                operation += witness.createTransitionOperation(witness.initializedState, target.findInitTransition())
            }
        }.optimize()

        if (witness.isSingleTrace) {
            witnessType.mainTransition += OxstsFactory.createSequentialTransition {
                for (transitionState in witness.transitionStates) {
                    operation += witness.createTransitionOperation(transitionState, target.findMainTransition())
                }
            }.optimize()
        } else {
            witnessType.mainTransition += OxstsFactory.createSequentialTransition {
                if (witness.transitionStates.any()) {
                    operation += OxstsFactory.createChoiceOperation().also {
                        for (transitionState in witness.transitionStates) {
                            it.operation += witness.createTransitionOperation(transitionState, target.findMainTransition())
                        }
                    }
                }
            }.optimize()
        }
    }

    private fun Witness.createTransitionOperation(transitionState: OxstsWitnessState, transition: Transition): SequenceOperation {
        return OxstsFactory.createSequenceOperation().also {
            it.operation += stateVariableAssumption(transitionState.id)

            it.operation += OxstsFactory.createInlineCall(OxstsFactory.createChainReferenceExpression(transition), isStatic = true)

            it.operation += transitionState.toAssumptionOperations()

            if (transitions[transitionState].any()) {
                it.operation += createStateTransitionChoiceOperation(transitionState)
            }
        }
    }

    private fun Witness.createStateTransitionChoiceOperation(transitionState: OxstsWitnessState): ChoiceOperation {
        return OxstsFactory.createChoiceOperation().also {
            for (nextState in transitions[transitionState]) {
                it.operation += OxstsFactory.createSequenceOperation().also {
                    it.operation += stateVariableAssignment(nextState.id)
                }
            }
        }
    }

    private fun stateVariableAssumption(value: Int): AssumptionOperation {
        return OxstsFactory.createEqualityAssumption(
            referenceExpression = OxstsFactory.createChainReferenceExpression(stateVariable),
            expression = OxstsFactory.createLiteralInteger(value)
        )
    }

    private fun stateVariableAssignment(value: Int): AssignmentOperation {
        return OxstsFactory.createAssignmentOperation(
            referenceExpression = OxstsFactory.createChainReferenceExpression(stateVariable),
            expression = OxstsFactory.createLiteralInteger(value)
        )
    }

}

class WitnessCreator(
    private val reader: OxstsReader
) {

    fun createWitness(targetName: String, cex: Cex): hu.bme.mit.semantifyr.oxsts.model.oxsts.Package {
        val target = reader.rootElements.flatMap { it.types }.filterIsInstance<Target>().first {
            it.name == targetName
        }

        val oxstsWitness = OxstsWitness(target, cex)

        return oxstsWitness.witnessPackage
    }

}
