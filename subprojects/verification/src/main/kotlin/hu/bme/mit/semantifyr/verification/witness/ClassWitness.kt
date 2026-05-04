/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.verification.witness

import hu.bme.mit.semantifyr.backend.witness.WitnessStateValue
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts

class ClassWitnessStateValue(
    val variableReference: Expression,
    val value: Expression,
)

class ClassWitnessState(
    val values: List<ClassWitnessStateValue>,
    val activatedTraces: List<WitnessStateValue>,
)

class ClassWitness(
    val initialState: ClassWitnessState,
    val initializedState: ClassWitnessState?,
    val transitionStates: List<ClassWitnessState>,
    val nextStateMap: Map<ClassWitnessState, List<ClassWitnessState>>,
    val inlinedOxsts: InlinedOxsts,
) {
    fun getNextStates(state: ClassWitnessState): List<ClassWitnessState> {
        return nextStateMap[state] ?: emptyList()
    }
}
