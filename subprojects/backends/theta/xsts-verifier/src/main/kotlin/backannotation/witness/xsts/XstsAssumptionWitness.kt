/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.theta.verification.backannotation.witness.xsts

import hu.bme.mit.semantifyr.backends.theta.verification.backannotation.witness.cex.CexAssumptionWitness
import hu.bme.mit.semantifyr.backends.theta.verification.backannotation.witness.cex.CexAssumptionWitnessState
import hu.bme.mit.semantifyr.backends.theta.verification.backannotation.witness.cex.CexAssumptionWitnessStateVariableValue
import hu.bme.mit.semantifyr.semantics.transformation.backannotation.AssumptionWitness
import hu.bme.mit.semantifyr.semantics.transformation.backannotation.AssumptionWitnessState
import hu.bme.mit.semantifyr.semantics.utils.SemantifyrUtils
import hu.bme.mit.semantifyr.xsts.lang.xsts.Expression
import hu.bme.mit.semantifyr.xsts.lang.xsts.VariableDeclaration
import hu.bme.mit.semantifyr.xsts.lang.xsts.XstsModel

class XstsAssumptionWitnessStateVariableValue(
    val variableDeclaration: VariableDeclaration,
    val value: Expression,
)

class XstsAssumptionWitnessState(
//    id: String,
    val variableValues: List<XstsAssumptionWitnessStateVariableValue>
) : AssumptionWitnessState()

class XstsAssumptionWitness(
    override val initialState: XstsAssumptionWitnessState,
    override val initializedState: XstsAssumptionWitnessState?,
    override val transitionStates: List<XstsAssumptionWitnessState>,
    override val nextStateMap: Map<XstsAssumptionWitnessState, List<XstsAssumptionWitnessState>>,
    val xstsModel: XstsModel
): AssumptionWitness<XstsAssumptionWitnessState>()

class XstsAssumptionWitnessTransformer {

    private inner class TransformerContext(val xstsModel: XstsModel) {
        val cexExpressionToXstsExpressionTransformer = CexExpressionToXstsExpressionTransformer(xstsModel)

        val mappings = mutableMapOf<CexAssumptionWitnessState, XstsAssumptionWitnessState>()

        fun transform(cexAssumptionWitnessState: CexAssumptionWitnessState) = mappings.getOrPut(cexAssumptionWitnessState) {
            XstsAssumptionWitnessState(
                cexAssumptionWitnessState.variableValues.filterNot {
                    SemantifyrUtils.isLocalVar(it.variableName)
                }.map {
                    transform(it)
                }
            )
        }

        private fun transform(variableValue: CexAssumptionWitnessStateVariableValue): XstsAssumptionWitnessStateVariableValue {
            val variable = xstsModel.variableDeclarations.firstOrNull {
                it.name == variableValue.variableName
            }
            check(variable != null) {
                "Variable with name ${variableValue.variableName} not found!"
            }

            return XstsAssumptionWitnessStateVariableValue(
                variable,
                cexExpressionToXstsExpressionTransformer.transform(variableValue.value)
            )
        }

    }

    fun transform(xstsModel: XstsModel, cexAssumptionWitness: CexAssumptionWitness): XstsAssumptionWitness {
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

        return XstsAssumptionWitness(
            initialState,
            initializedState,
            transitionStates,
            nextStateMap,
            xstsModel
        )
    }

}
