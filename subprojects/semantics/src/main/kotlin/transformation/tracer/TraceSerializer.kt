/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.semantics.transformation.tracer

import com.google.inject.Inject
import hu.bme.mit.semantifyr.oxsts.lang.naming.OxstsQualifiedNameProvider
import hu.bme.mit.semantifyr.oxsts.lang.serializer.ExpressionSerializer
import hu.bme.mit.semantifyr.semantics.expression.ExpressionEvaluationSerializer
import hu.bme.mit.semantifyr.semantics.transformation.backannotation.OxstsClassAssumptionActivatedTrace
import hu.bme.mit.semantifyr.semantics.transformation.backannotation.OxstsClassAssumptionWitness
import hu.bme.mit.semantifyr.semantics.transformation.backannotation.OxstsClassAssumptionWitnessState
import hu.bme.mit.semantifyr.semantics.transformation.backannotation.OxstsClassAssumptionWitnessStateValue
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream
import java.io.File

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

class TraceSerializer {

    @Inject
    private lateinit var oxstsQualifiedNameProvider: OxstsQualifiedNameProvider

    @Inject
    private lateinit var transitionCallTracer: TransitionCallTracer

    @Inject
    private lateinit var expressionEvaluationSerializer: ExpressionEvaluationSerializer

    @Inject
    private lateinit var expressionSerializer: ExpressionSerializer

    private fun transformInlinedOxstsAssumptionActivatedTrace(trace: OxstsClassAssumptionActivatedTrace): SerializableTrace {
        val transitionCallTrace = transitionCallTracer.getTransitionCallTrace(trace.traceOperation)

        return SerializableTrace(
            expressionEvaluationSerializer.serialize(transitionCallTrace.self),
            oxstsQualifiedNameProvider.getFullyQualifiedNameString(transitionCallTrace.transitionDeclaration),
            transitionCallTrace.arguments.map {
                SerializableTraceArgument(
                    it.parameterDeclaration?.name ?: "",
                    if (it.evaluation != null) {
                        expressionEvaluationSerializer.serialize(it.evaluation)
                    } else {
                        ""
                    }
                )
            },
            null // TODO: implement structural tracing -> call stack
        )
    }

    private fun transformInlinedOxstsAssumptionWitnessStateValue(value: OxstsClassAssumptionWitnessStateValue): SerializableTraceValue {
        val variable = expressionSerializer.serialize(value.variableReference)
        val value = expressionSerializer.serialize(value.value)

        return SerializableTraceValue(variable, value)
    }

    private fun transformInlinedOxstsAssumptionWitnessState(state: OxstsClassAssumptionWitnessState): SerializableTraceStep {
        return SerializableTraceStep(
            state.activatedTraces.map {
                transformInlinedOxstsAssumptionActivatedTrace(it)
            },
            state.values.map {
                transformInlinedOxstsAssumptionWitnessStateValue(it)
            }
        )
    }

    private fun transformWitness(inlinxOxstsAssumptionWitness: OxstsClassAssumptionWitness): SerializableTraceData {
        return SerializableTraceData(
            transformInlinedOxstsAssumptionWitnessState(inlinxOxstsAssumptionWitness.initialState),
            inlinxOxstsAssumptionWitness.transitionStates.map {
                transformInlinedOxstsAssumptionWitnessState(it)
            }
        )
    }

    fun serialize(inlinedOxstsAssumptionWitness: OxstsClassAssumptionWitness) {
        val data = transformWitness(inlinedOxstsAssumptionWitness)
        val path = inlinedOxstsAssumptionWitness.inlinedOxsts.eResource().uri.path().replace("inlined.oxsts", "trace.json")
        val file = File(path)

        val json = Json {
            prettyPrint = true
            prettyPrintIndent = "  "
            explicitNulls = false
        }

        file.outputStream().buffered().use {
            json.encodeToStream(data, it)
        }
    }

}
