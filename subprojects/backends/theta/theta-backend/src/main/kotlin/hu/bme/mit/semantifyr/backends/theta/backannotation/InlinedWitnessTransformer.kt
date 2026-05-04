/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.theta.backannotation

import com.google.inject.Inject
import hu.bme.mit.semantifyr.backend.scopes.VerificationScoped
import hu.bme.mit.semantifyr.backend.witness.InlinedWitness
import hu.bme.mit.semantifyr.backend.witness.WitnessState
import hu.bme.mit.semantifyr.backend.witness.WitnessStateValue
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration

@VerificationScoped
class InlinedWitnessTransformer {

    @Inject
    private lateinit var xstsExpressionToOxstsExpressionTransformer: XstsExpressionToOxstsExpressionTransformer

    private inner class TransformerContext(val inlinedOxsts: InlinedOxsts) {
        val mappings = mutableMapOf<XstsWitnessState, WitnessState>()
        val variablesByName: Map<String, VariableDeclaration> = inlinedOxsts.variables.associateBy {
            it.name
        }

        fun transform(xstsAssumptionWitnessState: XstsWitnessState) = mappings.getOrPut(xstsAssumptionWitnessState) {
            val stateVariableValues = xstsAssumptionWitnessState.variableValues

            WitnessState(
                transformStateValues(stateVariableValues),
            )
        }

        private fun transformStateValues(stateVariableValues: List<XstsWitnessStateVariableValue>): List<WitnessStateValue> {
            return stateVariableValues.map {
                transform(it)
            }
        }

        private fun transform(variableValue: XstsWitnessStateVariableValue): WitnessStateValue {
            return WitnessStateValue(
                resolveOxstsVariable(variableValue.variableDeclaration.name),
                xstsExpressionToOxstsExpressionTransformer.transform(variableValue.value),
            )
        }

        private fun resolveOxstsVariable(variableName: String): VariableDeclaration {
            return variablesByName[variableName]
                ?: error("Variable with name $variableName not found!")
        }
    }

    fun transform(inlinedOxsts: InlinedOxsts, xstsAssumptionWitness: XstsWitness): InlinedWitness {
        val context = TransformerContext(inlinedOxsts)

        val initialState = context.transform(xstsAssumptionWitness.initialState)
        val initializedState = if (xstsAssumptionWitness.initializedState != null) {
            context.transform(xstsAssumptionWitness.initializedState)
        } else {
            null
        }
        val transitionStates = xstsAssumptionWitness.transitionStates.map {
            context.transform(it)
        }
        val nextStateMap = xstsAssumptionWitness.nextStateMap.map {
            context.transform(it.key) to it.value.map { context.transform(it) }
        }.toMap()

        return InlinedWitness(
            initialState,
            initializedState,
            transitionStates,
            nextStateMap,
            inlinedOxsts,
        )
    }
}
