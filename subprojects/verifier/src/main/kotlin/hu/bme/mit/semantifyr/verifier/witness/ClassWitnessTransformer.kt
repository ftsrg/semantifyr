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
import hu.bme.mit.semantifyr.compiler.pipeline.utils.OxstsFactory
import hu.bme.mit.semantifyr.compiler.pipeline.utils.checkInterrupted
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ArithmeticUnaryOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralBoolean
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralInteger
import hu.bme.mit.semantifyr.oxsts.model.oxsts.UnaryOp
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration

class ClassWitnessTransformer @Inject constructor(
    private val instanceReferenceProvider: InstanceReferenceProvider,
) {

    private inner class TransformerContext(
        private val compilation: FlattenedCompilationContext,
    ) {
        fun transform(witnessState: WitnessState): ClassWitnessState {
            checkInterrupted()
            val (tracerVariableValues, nonTracerValues) = witnessState.values.partition {
                it.variable in compilation.transitionCallTraces.tracerVariables
            }
            val realVariableValues = nonTracerValues.filter {
                it.variable in compilation.flatteningInfo.variableHolders
            }
            val recordedArguments = nonTracerValues.filter {
                it.variable in compilation.transitionCallTraces.argumentVariables
            }.associate {
                it.variable to backAnnotateInstancePointers(it.variable, it.value)
            }

            return ClassWitnessState(
                realVariableValues.map {
                    transform(it)
                },
                tracerVariableValues.filter {
                    val value = it.value
                    value is LiteralBoolean && value.isValue
                },
                witnessState.clockValues.filter {
                    it.variable in compilation.flatteningInfo.variableHolders
                }.map {
                    ClassWitnessClockValue(locate(it.variable, compilation), it.value)
                },
                witnessState.elapsed,
                recordedArguments,
            )
        }

        private fun transform(variableValue: WitnessStateValue): ClassWitnessStateValue {
            return ClassWitnessStateValue(
                locate(variableValue.variable, compilation),
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

    fun locate(variable: VariableDeclaration, compilation: FlattenedCompilationContext): ClassWitnessVariable {
        val holder = compilation.flatteningInfo.variableHolders[variable]
            ?: error("Witness value references variable '${variable.name}' with no holder instance in flatteningInfo.variableHolders. Synthetic root-level variables should be filtered out before reaching locate(VariableDeclaration).")
        val arraySlot = compilation.flatteningInfo.arraySlots[variable]
        val recordField = compilation.flatteningInfo.recordFields[variable]
        val originalVariable = arraySlot?.arrayVariable
            ?: recordField?.recordVariable
            ?: compilation.flatteningInfo.resolveOriginalVariable(holder, variable)
        val holderReference = instanceReferenceProvider.getReference(holder)
        val memberReference = OxstsFactory.createNavigationSuffixExpression().also {
            it.primary = holderReference
            it.member = originalVariable
        }
        val variableReference = when {
            arraySlot != null -> OxstsFactory.createIndexingSuffixExpression().also {
                it.primary = memberReference
                it.index = OxstsFactory.createLiteralInteger(arraySlot.index)
            }
            recordField != null -> recordField.fields.fold<VariableDeclaration, Expression>(memberReference) { primary, field ->
                OxstsFactory.createNavigationSuffixExpression().also {
                    it.primary = primary
                    it.member = field
                }
            }
            else -> memberReference
        }

        return ClassWitnessVariable(
            variableReference,
            memberReference,
            originalVariable,
            arraySlot,
            recordField,
        )
    }

    fun transform(
        inlinedWitness: InlinedWitness,
        compilation: FlattenedCompilationContext,
    ): ClassWitness {
        val context = TransformerContext(compilation)

        val initializedState = context.transform(inlinedWitness.initializedState)
        val transitionStates = inlinedWitness.transitionStates.map {
            context.transform(it)
        }
        return ClassWitness(
            initializedState,
            transitionStates,
        )
    }

}
