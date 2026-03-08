/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.semantics.transformation.tracer

import com.google.inject.Inject
import hu.bme.mit.semantifyr.oxsts.lang.naming.OxstsQualifiedNameConverter
import hu.bme.mit.semantifyr.oxsts.lang.naming.OxstsQualifiedNameProvider
import hu.bme.mit.semantifyr.semantics.expression.ExpressionEvaluationSerializer
import hu.bme.mit.semantifyr.semantics.transformation.backannotation.InlinedOxstsAssumptionActivatedTrace
import hu.bme.mit.semantifyr.semantics.transformation.backannotation.InlinedOxstsAssumptionWitness
import hu.bme.mit.semantifyr.semantics.transformation.backannotation.InlinedOxstsAssumptionWitnessState
import hu.bme.mit.semantifyr.semantics.transformation.instantiation.InstanceNameProvider
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
data class SerializableTraceStep(
    val traces: List<SerializableTrace>,
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

    private fun transformInlinedOxstsAssumptionActivatedTrace(trace: InlinedOxstsAssumptionActivatedTrace): SerializableTrace {
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

    private fun transformInlinedOxstsAssumptionWitnessState(state: InlinedOxstsAssumptionWitnessState): SerializableTraceStep {
        return SerializableTraceStep(
            state.activatedTraces.map {
                transformInlinedOxstsAssumptionActivatedTrace(it)
            }
        )
    }

    private fun transformWitness(inlinxOxstsAssumptionWitness: InlinedOxstsAssumptionWitness): SerializableTraceData {
        return SerializableTraceData(
            transformInlinedOxstsAssumptionWitnessState(inlinxOxstsAssumptionWitness.initialState),
            inlinxOxstsAssumptionWitness.transitionStates.map {
                transformInlinedOxstsAssumptionWitnessState(it)
            }
        )
    }

    fun serialize(inlinxOxstsAssumptionWitness: InlinedOxstsAssumptionWitness) {
        val data = transformWitness(inlinxOxstsAssumptionWitness)
        val path = inlinxOxstsAssumptionWitness.inlinedOxsts.eResource().uri.path().replace("inlined.oxsts", "trace.json")
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
