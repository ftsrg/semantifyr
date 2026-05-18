/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backend.witness

import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration

class WitnessStateValue(
    val variable: VariableDeclaration,
    val value: Expression,
)

class WitnessState(
    val values: List<WitnessStateValue>,
)

class InlinedWitness(
    val initialState: WitnessState,
    val initializedState: WitnessState?,
    val transitionStates: List<WitnessState>,
    val nextStateMap: Map<WitnessState, List<WitnessState>>,
    val inlinedOxsts: InlinedOxsts,
) {
    fun getNextStates(state: WitnessState): List<WitnessState> {
        return nextStateMap[state] ?: emptyList()
    }
}

fun linearInlinedWitness(
    inlinedOxsts: InlinedOxsts,
    states: List<WitnessState>,
): InlinedWitness {
    require(states.isNotEmpty()) { "Cannot build witness from empty state list" }

    val initialState = WitnessState(values = emptyList())
    val initializedState = states.first()
    val transitionStates = states.drop(1)

    val nextStateMap = mutableMapOf<WitnessState, List<WitnessState>>()
    if (transitionStates.isNotEmpty()) {
        nextStateMap[initializedState] = listOf(transitionStates.first())
    }
    for (i in 1 until transitionStates.size) {
        nextStateMap[transitionStates[i - 1]] = listOf(transitionStates[i])
    }

    return InlinedWitness(
        initialState = initialState,
        initializedState = initializedState,
        transitionStates = transitionStates,
        nextStateMap = nextStateMap,
        inlinedOxsts = inlinedOxsts,
    )
}
