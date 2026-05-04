/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.verifier.witness

import com.google.inject.Inject
import hu.bme.mit.semantifyr.backend.witness.InlinedWitness
import hu.bme.mit.semantifyr.backend.witness.WitnessState
import hu.bme.mit.semantifyr.backend.witness.WitnessStateValue
import hu.bme.mit.semantifyr.compiler.pipeline.context.FlattenedCompilationContext
import hu.bme.mit.semantifyr.compiler.pipeline.expression.InstanceReferenceProvider
import hu.bme.mit.semantifyr.compiler.pipeline.inlining.TransitionTracerNames
import hu.bme.mit.semantifyr.compiler.pipeline.utils.OxstsFactory
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ArithmeticUnaryOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralInteger
import hu.bme.mit.semantifyr.oxsts.model.oxsts.UnaryOp
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration

class ClassWitnessTransformer @Inject constructor(
    private val instanceReferenceProvider: InstanceReferenceProvider,
) {

    private inner class TransformerContext(
        private val compilation: FlattenedCompilationContext,
    ) {
        val mappings = mutableMapOf<WitnessState, ClassWitnessState>()

        fun transform(witnessState: WitnessState) = mappings.getOrPut(witnessState) {
            val (tracerVariableValues, nonTracerValues) = witnessState.values.partition {
                TransitionTracerNames.isTracerName(it.variable.name)
            }
            val realVariableValues = nonTracerValues.filter {
                it.variable in compilation.flatteningInfo.variableHolders
            }

            ClassWitnessState(
                realVariableValues.map {
                    transform(it)
                },
                tracerVariableValues,
            )
        }

        private fun transform(variableValue: WitnessStateValue): ClassWitnessStateValue {
            val holder = compilation.flatteningInfo.variableHolders[variableValue.variable]
                ?: error("Witness value references variable '${variableValue.variable.name}' with no holder instance in flatteningInfo.variableHolders. Synthetic root-level variables should be filtered out before reaching transform(WitnessStateValue).")
            val originalVariable = compilation.flatteningInfo.resolveOriginalVariable(holder, variableValue.variable)
            val holderReference = instanceReferenceProvider.getReference(holder)
            val variableReference = OxstsFactory.createNavigationSuffixExpression().also {
                it.primary = holderReference
                it.member = originalVariable
            }

            return ClassWitnessStateValue(
                variableReference,
                backAnnotateInstancePointers(variableValue.variable, variableValue.value),
            )
        }

        private fun backAnnotateInstancePointers(
            variableDeclaration: VariableDeclaration,
            expression: Expression,
        ): Expression {
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

    }

    fun transform(
        inlinedWitness: InlinedWitness,
        compilation: FlattenedCompilationContext,
    ): ClassWitness {
        val context = TransformerContext(compilation)

        val initialState = context.transform(inlinedWitness.initialState)
        val initializedState = inlinedWitness.initializedState?.let {
            context.transform(it)
        }
        val transitionStates = inlinedWitness.transitionStates.map {
            context.transform(it)
        }
        val nextStateMap = inlinedWitness.nextStateMap.map {
            context.transform(it.key) to it.value.map { context.transform(it) }
        }.toMap()

        return ClassWitness(
            initialState,
            initializedState,
            transitionStates,
            nextStateMap,
            inlinedWitness.inlinedOxsts,
        )
    }

}
