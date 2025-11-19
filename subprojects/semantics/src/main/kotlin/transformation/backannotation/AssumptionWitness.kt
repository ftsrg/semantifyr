/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.semantics.transformation.backannotation

import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration

abstract class AssumptionWitnessState(
//    val id: String,
)

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
    val value: Expression
)

class InlinedOxstsAssumptionWitnessState(
//    id: String,
    val values: List<InlinedOxstsAssumptionWitnessStateValue>
) : AssumptionWitnessState()

class InlinedOxstsAssumptionWitness(
    override val initialState: InlinedOxstsAssumptionWitnessState,
    override val initializedState: InlinedOxstsAssumptionWitnessState?,
    override val transitionStates: List<InlinedOxstsAssumptionWitnessState>,
    override val nextStateMap: Map<InlinedOxstsAssumptionWitnessState, List<InlinedOxstsAssumptionWitnessState>>,
    val inlinedOxsts: InlinedOxsts
) : AssumptionWitness<InlinedOxstsAssumptionWitnessState>()

class OxstsClassAssumptionWitnessStateValue(
    val variableReference: Expression,
    val value: Expression
)

class OxstsClassAssumptionWitnessState(
//    id: String,
    val values: List<OxstsClassAssumptionWitnessStateValue>
) : AssumptionWitnessState()

class OxstsClassAssumptionWitness(
    override val initialState: OxstsClassAssumptionWitnessState,
    override val initializedState: OxstsClassAssumptionWitnessState?,
    override val transitionStates: List<OxstsClassAssumptionWitnessState>,
    override val nextStateMap: Map<OxstsClassAssumptionWitnessState, List<OxstsClassAssumptionWitnessState>>,
    val inlinedOxsts: InlinedOxsts
) : AssumptionWitness<OxstsClassAssumptionWitnessState>()
