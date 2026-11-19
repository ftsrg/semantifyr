/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.verifier.witness

import hu.bme.mit.semantifyr.backend.witness.Fraction
import hu.bme.mit.semantifyr.backend.witness.WitnessStateValue
import hu.bme.mit.semantifyr.compiler.pipeline.context.ArraySlot
import hu.bme.mit.semantifyr.compiler.pipeline.context.RecordField
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration

class ClassWitnessVariable(
    val reference: Expression,
    val declaredReference: Expression,
    val declaredVariable: VariableDeclaration,
    val arraySlot: ArraySlot?,
    val recordField: RecordField?,
)

class ClassWitnessStateValue(
    val variable: ClassWitnessVariable,
    val value: Expression,
)

class ClassWitnessClockValue(
    val variable: ClassWitnessVariable,
    val value: Fraction,
)

class ClassWitnessState(
    val values: List<ClassWitnessStateValue>,
    val activatedTraces: List<WitnessStateValue>,
    val clockValues: List<ClassWitnessClockValue>,
    val elapsed: Fraction,
    val recordedArguments: Map<VariableDeclaration, Expression>,
)

class ClassWitness(
    val initializedState: ClassWitnessState,
    val transitionStates: List<ClassWitnessState>,
)
