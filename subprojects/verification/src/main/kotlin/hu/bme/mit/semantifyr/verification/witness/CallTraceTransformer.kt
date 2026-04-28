/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.verification.witness

import com.google.inject.Inject
import hu.bme.mit.semantifyr.compiler.pipeline.context.TransitionCallTraceMap
import hu.bme.mit.semantifyr.compiler.pipeline.expression.ExpressionEvaluationSerializer
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
data class SerializableTraceValue(
    val variable: String,
    val value: String,
)

@Serializable
data class SerializableTraceStep(
    val traces: List<SerializableTrace>,
    val values: List<SerializableTraceValue>,
)

@Serializable
data class SerializableTraceData(
    val initialStep: SerializableTraceStep,
    val steps: List<SerializableTraceStep>,
)

class CallTraceTransformer @Inject constructor(
    private val oxstsQualifiedNameProvider: OxstsQualifiedNameProvider,
    private val expressionEvaluationSerializer: ExpressionEvaluationSerializer,
    private val expressionSerializer: ExpressionSerializer,
) {

    private fun transformActivatedTrace(
        trace: OxstsClassAssumptionActivatedTrace,
        traces: TransitionCallTraceMap,
    ): SerializableTrace {
        val transitionCallTrace = traces.getTransitionCallTrace(trace.traceOperation)

        return SerializableTrace(
            expressionEvaluationSerializer.serialize(transitionCallTrace.self),
            oxstsQualifiedNameProvider.getFullyQualifiedNameString(transitionCallTrace.transitionDeclaration),
            transitionCallTrace.arguments.map {
                SerializableTraceArgument(
                    it.parameterDeclaration?.name ?: "",
                    if (it.evaluation != null) {
                        expressionEvaluationSerializer.serialize(it.evaluation!!)
                    } else {
                        ""
                    },
                )
            },
            null,
        )
    }

    private fun transformStateValue(value: OxstsClassAssumptionWitnessStateValue): SerializableTraceValue {
        val variable = expressionSerializer.serialize(value.variableReference)
        val value = expressionSerializer.serialize(value.value)

        return SerializableTraceValue(variable, value)
    }

    private fun transformState(
        state: OxstsClassAssumptionWitnessState,
        traces: TransitionCallTraceMap,
    ): SerializableTraceStep {
        return SerializableTraceStep(
            state.activatedTraces.map {
                transformActivatedTrace(it, traces)
            },
            state.values.map {
                transformStateValue(it)
            },
        )
    }

    fun transformWitness(
        witness: OxstsClassAssumptionWitness,
        traces: TransitionCallTraceMap,
    ): SerializableTraceData {
        return SerializableTraceData(
            transformState(witness.initialState, traces),
            witness.transitionStates.map {
                transformState(it, traces)
            },
        )
    }

}
