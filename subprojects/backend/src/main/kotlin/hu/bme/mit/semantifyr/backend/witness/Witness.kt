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

class Witness(
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
