/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backend.witness

import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration

abstract class AssumptionWitnessState

abstract class AssumptionWitness<T : AssumptionWitnessState> {

    abstract val initialState: T
    abstract val initializedState: T?
    abstract val transitionStates: List<T>
    abstract val nextStateMap: Map<T, List<T>>

    fun getNextStates(state: T): List<T> {
        return nextStateMap[state] ?: emptyList()
    }

}

class InlinedOxstsAssumptionWitnessStateValue(
    val variable: VariableDeclaration,
    val value: Expression,
)

class InlinedOxstsAssumptionWitnessState(
    val values: List<InlinedOxstsAssumptionWitnessStateValue>,
) : AssumptionWitnessState()

class InlinedOxstsAssumptionWitness(
    override val initialState: InlinedOxstsAssumptionWitnessState,
    override val initializedState: InlinedOxstsAssumptionWitnessState?,
    override val transitionStates: List<InlinedOxstsAssumptionWitnessState>,
    override val nextStateMap: Map<InlinedOxstsAssumptionWitnessState, List<InlinedOxstsAssumptionWitnessState>>,
    val inlinedOxsts: InlinedOxsts,
) : AssumptionWitness<InlinedOxstsAssumptionWitnessState>()
