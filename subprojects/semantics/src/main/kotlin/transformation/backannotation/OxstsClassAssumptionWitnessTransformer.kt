/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.semantics.transformation.backannotation

import com.google.inject.Inject
import hu.bme.mit.semantifyr.semantics.expression.InstanceReferenceProvider
import hu.bme.mit.semantifyr.semantics.transformation.instantiation.InstanceManager
import hu.bme.mit.semantifyr.semantics.transformation.instantiation.OxstsInflator
import hu.bme.mit.semantifyr.semantics.utils.OxstsFactory

class OxstsClassAssumptionWitnessTransformer {

    @Inject
    private lateinit var instanceReferenceProvider: InstanceReferenceProvider

    @Inject
    private lateinit var instanceManager: InstanceManager

    @Inject
    private lateinit var oxstsInflator: OxstsInflator

    private inner class TransformerContext() {
        val mappings = mutableMapOf<InlinedOxstsAssumptionWitnessState, OxstsClassAssumptionWitnessState>()

        fun transform(inlinedOxstsAssumptionWitnessState: InlinedOxstsAssumptionWitnessState) = mappings.getOrPut(inlinedOxstsAssumptionWitnessState) {
            OxstsClassAssumptionWitnessState(
                inlinedOxstsAssumptionWitnessState.values.map {
                    transform(it)
                }
            )
        }

        private fun transform(variableValue: InlinedOxstsAssumptionWitnessStateValue): OxstsClassAssumptionWitnessStateValue {
            val holder = oxstsInflator.holderOfInlinedVariable(variableValue.variable)
            val originalVariable = instanceManager.resolveOriginalVariable(holder, variableValue.variable)
            val holderReference = instanceReferenceProvider.getReference(holder)
            val variableReference = OxstsFactory.createNavigationSuffixExpression().also {
                it.primary = holderReference
                it.member = originalVariable
            }

            return OxstsClassAssumptionWitnessStateValue(
                variableReference,
                oxstsInflator.backAnnotateInstancePointers(variableValue.variable, variableValue.value)
            )
        }

    }

    fun transform(inlinedOxstsAssumptionWitness: InlinedOxstsAssumptionWitness): OxstsClassAssumptionWitness {
        val context = TransformerContext()

        val initialState = context.transform(inlinedOxstsAssumptionWitness.initialState)
        val initializedState = context.transform(inlinedOxstsAssumptionWitness.initializedState)
        val transitionStates = inlinedOxstsAssumptionWitness.transitionStates.map {
            context.transform(it)
        }
        val nextStateMap = inlinedOxstsAssumptionWitness.nextStateMap.map {
            context.transform(it.key) to it.value.map { context.transform(it) }
        }.toMap()

        return OxstsClassAssumptionWitness(
            initialState,
            initializedState,
            transitionStates,
            nextStateMap,
            inlinedOxstsAssumptionWitness.inlinedOxsts
        )
    }

}
