/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backend.witness

import com.google.inject.Inject
import hu.bme.mit.semantifyr.compiler.pipeline.context.FlattenedCompilationContext
import hu.bme.mit.semantifyr.compiler.pipeline.expression.InstanceReferenceProvider
import hu.bme.mit.semantifyr.compiler.pipeline.utils.OxstsFactory
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ArithmeticUnaryOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralInteger
import hu.bme.mit.semantifyr.oxsts.model.oxsts.UnaryOp
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration

class OxstsClassAssumptionWitnessTransformer @Inject constructor(
    private val instanceReferenceProvider: InstanceReferenceProvider,
) {

    private inner class TransformerContext(
        private val compilation: FlattenedCompilationContext,
    ) {
        val mappings = mutableMapOf<InlinedOxstsAssumptionWitnessState, OxstsClassAssumptionWitnessState>()

        fun transform(inlinedOxstsAssumptionWitnessState: InlinedOxstsAssumptionWitnessState) = mappings.getOrPut(inlinedOxstsAssumptionWitnessState) {
            OxstsClassAssumptionWitnessState(
                inlinedOxstsAssumptionWitnessState.values.map {
                    transform(it)
                },
                inlinedOxstsAssumptionWitnessState.activatedTraces.map {
                    transform(it)
                },
            )
        }

        private fun transform(variableValue: InlinedOxstsAssumptionWitnessStateValue): OxstsClassAssumptionWitnessStateValue {
            val holder = compilation.flatteningInfo.variableHolders[variableValue.variable] ?: error("Variable was not transformed!")
            val originalVariable = compilation.flatteningInfo.resolveOriginalVariable(holder, variableValue.variable)
            val holderReference = instanceReferenceProvider.getReference(holder)
            val variableReference = OxstsFactory.createNavigationSuffixExpression().also {
                it.primary = holderReference
                it.member = originalVariable
            }

            return OxstsClassAssumptionWitnessStateValue(
                variableReference,
                backAnnotateInstancePointers(variableValue.variable, variableValue.value),
            )
        }

        private fun backAnnotateInstancePointers(variableDeclaration: VariableDeclaration, expression: Expression): Expression {
            if (compilation.flatteningInfo.variableInstanceDomains[variableDeclaration] == null) {
                return expression
            }

            if (expression is ArithmeticUnaryOperator && expression.op == UnaryOp.MINUS) {
                val body = expression.body
                if (body is LiteralInteger && body.value == 1) {
                    return OxstsFactory.createLiteralNothing()
                }
            }

            require(expression is LiteralInteger)

            val instance = compilation.flatteningInfo.instanceIdMapping.instanceOfId(expression.value)

            return instanceReferenceProvider.getReference(instance)
        }

        private fun transform(trace: InlinedOxstsAssumptionActivatedTrace): OxstsClassAssumptionActivatedTrace {
            return OxstsClassAssumptionActivatedTrace(
                trace.traceOperation,
            )
        }

    }

    fun transform(inlinedOxstsAssumptionWitness: InlinedOxstsAssumptionWitness, compilation: FlattenedCompilationContext): OxstsClassAssumptionWitness {
        val context = TransformerContext(compilation)

        val initialState = context.transform(inlinedOxstsAssumptionWitness.initialState)
        val initializedState = if (inlinedOxstsAssumptionWitness.initializedState != null) {
            context.transform(inlinedOxstsAssumptionWitness.initializedState)
        } else {
            null
        }
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
            inlinedOxstsAssumptionWitness.inlinedOxsts,
        )
    }

}
