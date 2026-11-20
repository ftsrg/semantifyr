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

class WitnessClockValue(
    val variable: VariableDeclaration,
    val value: Fraction,
)

class WitnessState(
    val values: List<WitnessStateValue>,
    val clockValues: List<WitnessClockValue>,
    val elapsed: Fraction,
)

class InlinedWitness(
    val initializedState: WitnessState,
    val transitionStates: List<WitnessState>,
    val inlinedOxsts: InlinedOxsts,
)

fun linearInlinedWitness(
    inlinedOxsts: InlinedOxsts,
    states: List<WitnessState>,
): InlinedWitness {
    require(states.isNotEmpty()) { "Cannot build witness from empty state list" }

    return InlinedWitness(
        initializedState = states.first(),
        transitionStates = states.drop(1),
        inlinedOxsts = inlinedOxsts,
    )
}
