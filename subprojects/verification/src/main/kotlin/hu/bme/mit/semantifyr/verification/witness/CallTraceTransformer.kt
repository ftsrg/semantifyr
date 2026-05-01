/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.verification.witness

import com.google.inject.Inject
import hu.bme.mit.semantifyr.compiler.pipeline.context.TransitionCallTraceMap
import hu.bme.mit.semantifyr.compiler.pipeline.expression.ExpressionEvaluationSerializer
import hu.bme.mit.semantifyr.compiler.pipeline.inlining.TransitionCallTrace
import hu.bme.mit.semantifyr.oxsts.lang.naming.OxstsQualifiedNameProvider
import hu.bme.mit.semantifyr.oxsts.lang.serializer.ExpressionSerializer
import kotlinx.serialization.Serializable

@Serializable
data class SerializableTraceArgument(
    val parameter: String,
    val value: String,
)

@Serializable
data class SerializableTrace(
    val self: String,
    val calledTransition: String,
    val arguments: List<SerializableTraceArgument>,
    val innerTraces: List<SerializableTrace>?,
)

@Serializable
data class SerializableCallTraceStep(
    val traces: List<SerializableTrace>,
)

@Serializable
data class SerializableCallTraceData(
    val initialStep: SerializableCallTraceStep,
    val steps: List<SerializableCallTraceStep>,
)

@Serializable
data class SerializableWitnessStateValue(
    val variable: String,
    val value: String,
)

@Serializable
data class SerializableWitnessStateStep(
    val values: List<SerializableWitnessStateValue>,
)

@Serializable
data class SerializableWitnessStateData(
    val initialStep: SerializableWitnessStateStep,
    val steps: List<SerializableWitnessStateStep>,
)

class CallTraceTransformer @Inject constructor(
    private val oxstsQualifiedNameProvider: OxstsQualifiedNameProvider,
    private val expressionEvaluationSerializer: ExpressionEvaluationSerializer,
    private val expressionSerializer: ExpressionSerializer,
) {

    fun transformCallTrace(
        witness: OxstsClassAssumptionWitness,
        traces: TransitionCallTraceMap,
    ): SerializableCallTraceData {
        return SerializableCallTraceData(
            transformCallTraceStep(witness.initialState, traces),
            witness.transitionStates.map {
                transformCallTraceStep(it, traces)
            },
        )
    }

    fun transformWitnessState(witness: OxstsClassAssumptionWitness): SerializableWitnessStateData {
        return SerializableWitnessStateData(
            transformWitnessStateStep(witness.initialState),
            witness.transitionStates.map {
                transformWitnessStateStep(it)
            },
        )
    }

    private fun transformCallTraceStep(
        state: OxstsClassAssumptionWitnessState,
        traces: TransitionCallTraceMap,
    ): SerializableCallTraceStep {
        val activatedTraces = state.activatedTraces.map {
            traces.getTransitionCallTrace(it.tracerVariable)
        }
        return SerializableCallTraceStep(buildForest(activatedTraces))
    }

    private fun transformWitnessStateStep(state: OxstsClassAssumptionWitnessState): SerializableWitnessStateStep {
        return SerializableWitnessStateStep(
            state.values.map {
                transformStateValue(it)
            },
        )
    }

    private fun buildForest(activatedTraces: List<TransitionCallTrace>): List<SerializableTrace> {
        val activatedSet = activatedTraces.toSet()
        val childrenByParent = activatedTraces.groupBy {
            it.parent.takeIf { parent -> parent in activatedSet }
        }

        fun build(trace: TransitionCallTrace): SerializableTrace {
            val children = childrenByParent[trace].orEmpty().map {
                build(it)
            }
            return SerializableTrace(
                self = expressionEvaluationSerializer.serialize(trace.self),
                calledTransition = oxstsQualifiedNameProvider.getFullyQualifiedNameString(trace.transitionDeclaration),
                arguments = trace.arguments.map {
                    SerializableTraceArgument(
                        it.parameterDeclaration.name,
                        expressionEvaluationSerializer.serialize(it.evaluation),
                    )
                },
                innerTraces = children.takeIf { it.isNotEmpty() },
            )
        }

        return childrenByParent[null].orEmpty().map {
            build(it)
        }
    }

    private fun transformStateValue(value: OxstsClassAssumptionWitnessStateValue): SerializableWitnessStateValue {
        return SerializableWitnessStateValue(
            expressionSerializer.serialize(value.variableReference),
            expressionSerializer.serialize(value.value),
        )
    }

}
