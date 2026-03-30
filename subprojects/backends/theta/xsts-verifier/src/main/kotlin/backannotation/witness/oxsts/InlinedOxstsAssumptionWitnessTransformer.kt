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
import hu.bme.mit.semantifyr.backends.theta.verification.transformation.xsts.TraceOperationTransformer
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.semantics.transformation.backannotation.InlinedOxstsAssumptionActivatedTrace
import hu.bme.mit.semantifyr.semantics.transformation.backannotation.InlinedOxstsAssumptionWitness
import hu.bme.mit.semantifyr.semantics.transformation.backannotation.InlinedOxstsAssumptionWitnessState
import hu.bme.mit.semantifyr.semantics.transformation.backannotation.InlinedOxstsAssumptionWitnessStateValue
import hu.bme.mit.semantifyr.semantics.transformation.injection.scope.CompilationScoped
import hu.bme.mit.semantifyr.xsts.lang.xsts.LiteralBoolean
import hu.bme.mit.semantifyr.xsts.lang.xsts.TopLevelVariableDeclaration

@CompilationScoped
class InlinedOxstsAssumptionWitnessTransformer {

    @Inject
    private lateinit var xstsExpressionToOxstsExpressionTransformer: XstsExpressionToOxstsExpressionTransformer

    @Inject
    private lateinit var traceOperationTransformer: TraceOperationTransformer

    private inner class TransformerContext(val inlinedOxsts: InlinedOxsts) {
        val mappings = mutableMapOf<XstsAssumptionWitnessState, InlinedOxstsAssumptionWitnessState>()

        fun transform(xstsAssumptionWitnessState: XstsAssumptionWitnessState) = mappings.getOrPut(xstsAssumptionWitnessState) {
            val stateVariableValues = xstsAssumptionWitnessState.variableValues.filter {
                traceOperationTransformer.isTracerVariable(it.variableDeclaration as TopLevelVariableDeclaration) == false
            }
            val traceVariableValues = xstsAssumptionWitnessState.variableValues.filter {
                traceOperationTransformer.isTracerVariable(it.variableDeclaration as TopLevelVariableDeclaration)
            }

            InlinedOxstsAssumptionWitnessState(
                transformStateValues(stateVariableValues),
                transformTraceValues(traceVariableValues),
            )
        }

        private fun transformStateValues(stateVariableValues: List<XstsAssumptionWitnessStateVariableValue>): List<InlinedOxstsAssumptionWitnessStateValue> {
            return stateVariableValues.map {
                transform(it)
            }
        }

        private fun transformTraceValues(traceVariableValues: List<XstsAssumptionWitnessStateVariableValue>): List<InlinedOxstsAssumptionActivatedTrace> {
            return traceVariableValues.filter {
                it.value is LiteralBoolean && it.value.isValue
            }.map {
                InlinedOxstsAssumptionActivatedTrace(
                    traceOperationTransformer.getTraceOperation(it.variableDeclaration as TopLevelVariableDeclaration),
                )
            }
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
                xstsExpressionToOxstsExpressionTransformer.transform(variableValue.value),
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
            inlinedOxsts,
        )
    }

}
