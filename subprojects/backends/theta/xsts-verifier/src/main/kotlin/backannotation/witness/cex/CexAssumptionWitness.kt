/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.theta.verification.backannotation.witness.cex

import hu.bme.mit.semantifyr.cex.lang.cex.CexModel
import hu.bme.mit.semantifyr.cex.lang.cex.ExplStateValue
import hu.bme.mit.semantifyr.cex.lang.cex.ExplVariableValue
import hu.bme.mit.semantifyr.cex.lang.cex.Expression
import hu.bme.mit.semantifyr.cex.lang.cex.XstsState
import hu.bme.mit.semantifyr.semantics.transformation.backannotation.AssumptionWitness
import hu.bme.mit.semantifyr.semantics.transformation.backannotation.AssumptionWitnessState

class CexAssumptionWitnessStateVariableValue(
    val variableName: String,
    val value: Expression,
)

private fun ExplVariableValue.toCexVariable(): CexAssumptionWitnessStateVariableValue {
    return CexAssumptionWitnessStateVariableValue(
        variable,
        value
    )
}

class CexAssumptionWitnessState(
//    id: String,
    val variableValues: List<CexAssumptionWitnessStateVariableValue>
) : AssumptionWitnessState()

private fun XstsState.toCexState(): CexAssumptionWitnessState {
    val state = stateValue

    require(state is ExplStateValue)

    return CexAssumptionWitnessState(
        state.variableValues.map { it.toCexVariable() }
    )
}

class CexAssumptionWitness(
    override val initialState: CexAssumptionWitnessState,
    override val initializedState: CexAssumptionWitnessState,
    override val transitionStates: List<CexAssumptionWitnessState>,
    override val nextStateMap: Map<CexAssumptionWitnessState, List<CexAssumptionWitnessState>>,
): AssumptionWitness<CexAssumptionWitnessState>()

private val XstsState.isTran
    get() = isPostInit && isLastInternal
private val XstsState.isInitial
    get() = isPreInit && isLastEnv
private val XstsState.isInitialized
    get() = isPostInit && isLastInternal

class CexAssumptionWitnessTransformer {

    fun transform(cexModel: CexModel): CexAssumptionWitness {
        val initialState = cexModel.states.first()
        val initializedState = cexModel.states[1]
        val states = cexModel.states.drop(2).filter {
            it.isTran
        }

        require(initialState.isInitial)
        require(initializedState.isInitialized)

        val initialCexState = initialState.toCexState()
        val initializedCexState = initializedState.toCexState()
        val cexStates = states.map { it.toCexState() }

        val nextStateMap = buildMap {
            put(initialCexState, listOf(initializedCexState))

            if (cexStates.isEmpty()) {
                put(initializedCexState, emptyList())
                return@buildMap
            }
            put(initializedCexState, listOf(cexStates.first()))

            for (index in cexStates.indices.drop(1)) {
                put(cexStates[index - 1], listOf(cexStates[index]))
            }
        }

        return CexAssumptionWitness(
            initialCexState,
            initializedCexState,
            cexStates,
            nextStateMap
        )
    }

}
