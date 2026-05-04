/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.theta.backannotation

import hu.bme.mit.semantifyr.cex.lang.cex.CexModel
import hu.bme.mit.semantifyr.cex.lang.cex.ExplStateValue
import hu.bme.mit.semantifyr.cex.lang.cex.ExplVariableValue
import hu.bme.mit.semantifyr.cex.lang.cex.Expression
import hu.bme.mit.semantifyr.cex.lang.cex.XstsState

class CexWitnessStateVariableValue(
    val variableName: String,
    val value: Expression,
)

private fun ExplVariableValue.toCexVariable(): CexWitnessStateVariableValue {
    return CexWitnessStateVariableValue(
        variable,
        value,
    )
}

class CexWitnessState(
    val variableValues: List<CexWitnessStateVariableValue>,
)

private fun XstsState.toCexState(): CexWitnessState {
    val state = stateValue

    require(state is ExplStateValue)

    return CexWitnessState(
        state.variableValues.map {
            it.toCexVariable()
        },
    )
}

class CexWitness(
    val initialState: CexWitnessState,
    val initializedState: CexWitnessState?,
    val transitionStates: List<CexWitnessState>,
    val nextStateMap: Map<CexWitnessState, List<CexWitnessState>>,
) {
    fun getNextStates(state: CexWitnessState): List<CexWitnessState> {
        return nextStateMap[state] ?: emptyList()
    }
}

private val XstsState.isTran
    get() = isPostInit && isLastInternal
private val XstsState.isInitial
    get() = isPreInit && isLastEnv
private val XstsState.isInitialized
    get() = isPostInit && isLastInternal

class CexWitnessTransformer {

    fun transform(cexModel: CexModel): CexWitness {
        val initialState = cexModel.states.first()
        val initializedState = cexModel.states.getOrNull(1)
        val states = cexModel.states.drop(2).filter {
            it.isTran
        }

        require(initialState.isInitial)
        require(initializedState?.isInitialized != false)

        val initialCexState = initialState.toCexState()
        val initializedCexState = initializedState?.toCexState()
        val cexStates = states.map { it.toCexState() }

        val nextStateMap = buildMap {
            if (initializedCexState != null) {
                put(initialCexState, listOf(initializedCexState))

                if (cexStates.isEmpty()) {
                    put(initializedCexState, emptyList())
                } else {
                    put(initializedCexState, listOf(cexStates.first()))

                    for (index in cexStates.indices.drop(1)) {
                        put(cexStates[index - 1], listOf(cexStates[index]))
                    }
                }
            }
        }

        return CexWitness(
            initialCexState,
            initializedCexState,
            cexStates,
            nextStateMap,
        )
    }
}
