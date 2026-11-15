/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.theta.backannotation

import com.google.inject.Inject
import hu.bme.mit.semantifyr.backend.transformation.WHOLE_DELAY_VARIABLE_NAME
import hu.bme.mit.semantifyr.backend.witness.Fraction
import hu.bme.mit.semantifyr.backend.witness.InlinedWitness
import hu.bme.mit.semantifyr.backend.witness.WitnessFailedException
import hu.bme.mit.semantifyr.backend.witness.WitnessState
import hu.bme.mit.semantifyr.backend.witness.WitnessStateValue
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration

class InlinedWitnessTransformer @Inject constructor(
    private val xstsExpressionToOxstsExpressionTransformer: XstsExpressionToOxstsExpressionTransformer,
) {

    private inner class TransformerContext(val inlinedOxsts: InlinedOxsts) {
        val mappings = mutableMapOf<XstsWitnessState, WitnessState>()
        val variablesByName: Map<String, VariableDeclaration> = inlinedOxsts.variables.associateBy {
            it.name
        }

        fun transform(xstsAssumptionWitnessState: XstsWitnessState) = mappings.getOrPut(xstsAssumptionWitnessState) {
            val (delays, stateVariableValues) = xstsAssumptionWitnessState.variableValues.partition {
                it.variableDeclaration.name == WHOLE_DELAY_VARIABLE_NAME
            }

            WitnessState(
                transformStateValues(stateVariableValues),
                elapsed = delays.singleOrNull()?.let {
                    Fraction.of(xstsExpressionToOxstsExpressionTransformer.transformInteger(it.value))
                } ?: Fraction.ZERO,
            )
        }

        private fun transformStateValues(stateVariableValues: List<XstsWitnessStateVariableValue>): List<WitnessStateValue> {
            return stateVariableValues.map {
                transform(it)
            }
        }

        private fun transform(variableValue: XstsWitnessStateVariableValue): WitnessStateValue {
            val variable = resolveOxstsVariable(variableValue.variableDeclaration.name)
            return WitnessStateValue(
                variable,
                xstsExpressionToOxstsExpressionTransformer.transform(variableValue.value, variable),
            )
        }

        private fun resolveOxstsVariable(variableName: String): VariableDeclaration {
            return variablesByName[variableName]
                ?: throw WitnessFailedException("Theta's trace holds $variableName, which names no variable of the model")
        }
    }

    fun transform(inlinedOxsts: InlinedOxsts, xstsAssumptionWitness: XstsWitness): InlinedWitness {
        val context = TransformerContext(inlinedOxsts)

        val initializedState = context.transform(xstsAssumptionWitness.initializedState)
        val transitionStates = xstsAssumptionWitness.transitionStates.map {
            context.transform(it)
        }
        val nextStateMap = xstsAssumptionWitness.nextStateMap.map {
            context.transform(it.key) to it.value.map { context.transform(it) }
        }.toMap()

        return InlinedWitness(
            initializedState,
            transitionStates,
            nextStateMap,
            inlinedOxsts,
        )
    }
}
