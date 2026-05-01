/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.verification.witness

import hu.bme.mit.semantifyr.backend.witness.AssumptionWitness
import hu.bme.mit.semantifyr.backend.witness.AssumptionWitnessState
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration

class OxstsClassAssumptionWitnessStateValue(
    val variableReference: Expression,
    val value: Expression,
)

class OxstsClassAssumptionActivatedTrace(
    val tracerVariable: VariableDeclaration,
)

class OxstsClassAssumptionWitnessState(
    val values: List<OxstsClassAssumptionWitnessStateValue>,
    val activatedTraces: List<OxstsClassAssumptionActivatedTrace>,
) : AssumptionWitnessState()

class OxstsClassAssumptionWitness(
    override val initialState: OxstsClassAssumptionWitnessState,
    override val initializedState: OxstsClassAssumptionWitnessState?,
    override val transitionStates: List<OxstsClassAssumptionWitnessState>,
    override val nextStateMap: Map<OxstsClassAssumptionWitnessState, List<OxstsClassAssumptionWitnessState>>,
    val inlinedOxsts: InlinedOxsts,
) : AssumptionWitness<OxstsClassAssumptionWitnessState>()
