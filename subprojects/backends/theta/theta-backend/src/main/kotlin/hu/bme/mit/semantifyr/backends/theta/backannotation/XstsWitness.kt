/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.theta.backannotation

import hu.bme.mit.semantifyr.compiler.pipeline.inlining.LocalVarNames
import hu.bme.mit.semantifyr.xsts.lang.xsts.Expression
import hu.bme.mit.semantifyr.xsts.lang.xsts.VariableDeclaration
import hu.bme.mit.semantifyr.xsts.lang.xsts.XstsModel

class XstsWitnessStateVariableValue(
    val variableDeclaration: VariableDeclaration,
    val value: Expression,
)

class XstsWitnessState(
    val variableValues: List<XstsWitnessStateVariableValue>,
)

class XstsWitness(
    val initialState: XstsWitnessState,
    val initializedState: XstsWitnessState?,
    val transitionStates: List<XstsWitnessState>,
    val nextStateMap: Map<XstsWitnessState, List<XstsWitnessState>>,
    val xstsModel: XstsModel,
) {
    fun getNextStates(state: XstsWitnessState): List<XstsWitnessState> {
        return nextStateMap[state] ?: emptyList()
    }
}

class XstsWitnessTransformer {

    private inner class TransformerContext(val xstsModel: XstsModel) {
        val cexExpressionToXstsExpressionTransformer = CexExpressionToXstsExpressionTransformer(xstsModel)

        val mappings = mutableMapOf<CexWitnessState, XstsWitnessState>()

        fun transform(cexAssumptionWitnessState: CexWitnessState) = mappings.getOrPut(cexAssumptionWitnessState) {
            XstsWitnessState(
                cexAssumptionWitnessState.variableValues.filterNot {
                    LocalVarNames.isLocalVar(it.variableName)
                }.map {
                    transform(it)
                },
            )
        }

        private fun transform(variableValue: CexWitnessStateVariableValue): XstsWitnessStateVariableValue {
            val variable = xstsModel.variableDeclarations.firstOrNull {
                it.name == variableValue.variableName
            }
            check(variable != null) {
                "Variable with name ${variableValue.variableName} not found!"
            }

            return XstsWitnessStateVariableValue(
                variable,
                cexExpressionToXstsExpressionTransformer.transform(variableValue.value),
            )
        }
    }

    fun transform(xstsModel: XstsModel, cexAssumptionWitness: CexWitness): XstsWitness {
        val context = TransformerContext(xstsModel)

        val initialState = context.transform(cexAssumptionWitness.initialState)
        val initializedState = if (cexAssumptionWitness.initializedState != null) {
            context.transform(cexAssumptionWitness.initializedState)
        } else {
            null
        }
        val transitionStates = cexAssumptionWitness.transitionStates.map {
            context.transform(it)
        }
        val nextStateMap = cexAssumptionWitness.nextStateMap.map {
            context.transform(it.key) to it.value.map { context.transform(it) }
        }.toMap()

        return XstsWitness(
            initialState,
            initializedState,
            transitionStates,
            nextStateMap,
            xstsModel,
        )
    }
}
