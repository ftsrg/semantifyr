/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.theta.backannotation

import hu.bme.mit.semantifyr.backend.witness.WitnessFailedException
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
    val initializedState: XstsWitnessState,
    val transitionStates: List<XstsWitnessState>,
    val xstsModel: XstsModel,
)

class XstsWitnessTransformer {

    private class TransformerContext(val xstsModel: XstsModel) {
        val cexExpressionToXstsExpressionTransformer = CexExpressionToXstsExpressionTransformer(xstsModel)

        // Theta 8 prints a state's variables sorted by name, the witness keeps the declaration order
        private val declarationOrder = xstsModel.variableDeclarations.withIndex().associate {
            it.value.name to it.index
        }

        fun transform(cexAssumptionWitnessState: CexWitnessState): XstsWitnessState {
            return XstsWitnessState(
                cexAssumptionWitnessState.variableValues.filterNot {
                    LocalVarNames.isLocalVar(it.variableName)
                }.sortedBy {
                    declarationOrder[it.variableName]
                }.map {
                    transform(it)
                },
            )
        }

        private fun transform(variableValue: CexWitnessStateVariableValue): XstsWitnessStateVariableValue {
            val index = declarationOrder[variableValue.variableName]
                ?: throw WitnessFailedException("Theta's trace holds ${variableValue.variableName}, which names no variable of the model")
            val variable = xstsModel.variableDeclarations[index]

            return XstsWitnessStateVariableValue(
                variable,
                cexExpressionToXstsExpressionTransformer.transform(variableValue.value),
            )
        }
    }

    fun transform(xstsModel: XstsModel, cexAssumptionWitness: CexWitness): XstsWitness {
        val context = TransformerContext(xstsModel)

        val initializedState = context.transform(cexAssumptionWitness.initializedState)
        val transitionStates = cexAssumptionWitness.transitionStates.map {
            context.transform(it)
        }

        return XstsWitness(
            initializedState,
            transitionStates,
            xstsModel,
        )
    }
}
