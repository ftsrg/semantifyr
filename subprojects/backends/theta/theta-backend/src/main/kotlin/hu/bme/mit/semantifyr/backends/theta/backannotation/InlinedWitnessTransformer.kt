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
import hu.bme.mit.semantifyr.backend.witness.TraceVariableResolver
import hu.bme.mit.semantifyr.backend.witness.WitnessState
import hu.bme.mit.semantifyr.backend.witness.WitnessStateValue
import hu.bme.mit.semantifyr.backends.theta.transformation.xsts.ThetaVariableTransformer
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts

class InlinedWitnessTransformer @Inject constructor(
    private val xstsExpressionToOxstsExpressionTransformer: XstsExpressionToOxstsExpressionTransformer,
    private val thetaVariableTransformer: ThetaVariableTransformer,
) {

    private inner class TransformerContext(
        inlinedOxsts: InlinedOxsts,
        addedNames: Set<String>,
    ) {
        val resolver = TraceVariableResolver(inlinedOxsts, thetaVariableTransformer, addedNames, "Theta's trace")

        fun transform(xstsAssumptionWitnessState: XstsWitnessState): WitnessState {
            val (delays, stateVariableValues) = xstsAssumptionWitnessState.variableValues.partition {
                it.variableDeclaration.name == WHOLE_DELAY_VARIABLE_NAME
            }

            return WitnessState(
                transformStateValues(stateVariableValues),
                elapsed = delays.singleOrNull()?.let {
                    Fraction.of(xstsExpressionToOxstsExpressionTransformer.transformInteger(it.value))
                } ?: Fraction.ZERO,
            )
        }

        private fun transformStateValues(stateVariableValues: List<XstsWitnessStateVariableValue>): List<WitnessStateValue> {
            return stateVariableValues.mapNotNull {
                transform(it)
            }
        }

        private fun transform(variableValue: XstsWitnessStateVariableValue): WitnessStateValue? {
            val variable = resolver.stateVariableOf(variableValue.variableDeclaration.name) ?: return null
            return WitnessStateValue(
                variable,
                xstsExpressionToOxstsExpressionTransformer.transform(variableValue.value, variable),
            )
        }
    }

    fun transform(
        inlinedOxsts: InlinedOxsts,
        xstsAssumptionWitness: XstsWitness,
        addedNames: Set<String>,
    ): InlinedWitness {
        val context = TransformerContext(inlinedOxsts, addedNames)

        val initializedState = context.transform(xstsAssumptionWitness.initializedState)
        val transitionStates = xstsAssumptionWitness.transitionStates.map {
            context.transform(it)
        }

        return InlinedWitness(
            initializedState,
            transitionStates,
            inlinedOxsts,
        )
    }
}
