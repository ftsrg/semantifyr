/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.theta.verification.backannotation.witness.oxsts

import com.google.inject.Inject
import hu.bme.mit.semantifyr.backends.theta.verification.backannotation.witness.inlinedoxsts.XstsExpressionToOxstsExpressionTransformer
import hu.bme.mit.semantifyr.backends.theta.verification.backannotation.witness.xsts.XstsAssumptionWitness
import hu.bme.mit.semantifyr.backends.theta.verification.backannotation.witness.xsts.XstsAssumptionWitnessState
import hu.bme.mit.semantifyr.backends.theta.verification.backannotation.witness.xsts.XstsAssumptionWitnessStateVariableValue
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.semantics.transformation.backannotation.InlinedOxstsAssumptionWitness
import hu.bme.mit.semantifyr.semantics.transformation.backannotation.InlinedOxstsAssumptionWitnessState
import hu.bme.mit.semantifyr.semantics.transformation.backannotation.InlinedOxstsAssumptionWitnessStateValue
import hu.bme.mit.semantifyr.semantics.transformation.injection.scope.CompilationScoped

@CompilationScoped
class InlinedOxstsAssumptionWitnessTransformer {

    @Inject
    private lateinit var xstsExpressionToOxstsExpressionTransformer: XstsExpressionToOxstsExpressionTransformer

    private inner class TransformerContext(val inlinedOxsts: InlinedOxsts) {
        val mappings = mutableMapOf<XstsAssumptionWitnessState, InlinedOxstsAssumptionWitnessState>()

        fun transform(xstsAssumptionWitnessState: XstsAssumptionWitnessState) = mappings.getOrPut(xstsAssumptionWitnessState) {
            InlinedOxstsAssumptionWitnessState(
                xstsAssumptionWitnessState.variableValues.map {
                    transform(it)
                }
            )
        }

        private fun transform(variableValue: XstsAssumptionWitnessStateVariableValue): InlinedOxstsAssumptionWitnessStateValue {
            val variable = inlinedOxsts.variables.firstOrNull {
                it.name == variableValue.variableDeclaration.name
            }
            check(variable != null) {
                "Variable with name ${variableValue.variableDeclaration.name} not found!"
            }

            return InlinedOxstsAssumptionWitnessStateValue(
                variable,
                xstsExpressionToOxstsExpressionTransformer.transform(variableValue.value)
            )
        }

    }

    fun transform(inlinedOxsts: InlinedOxsts, xstsAssumptionWitness: XstsAssumptionWitness): InlinedOxstsAssumptionWitness {
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

        return InlinedOxstsAssumptionWitness(
            initialState,
            initializedState,
            transitionStates,
            nextStateMap,
            inlinedOxsts
        )
    }

}
