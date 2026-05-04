/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.verifier.witness

import com.google.inject.Inject
import hu.bme.mit.semantifyr.compiler.pipeline.context.TransitionCallTraceMap
import hu.bme.mit.semantifyr.compiler.pipeline.expression.ExpressionEvaluationSerializer
import hu.bme.mit.semantifyr.compiler.pipeline.inlining.TransitionCallTrace
import hu.bme.mit.semantifyr.oxsts.lang.naming.OxstsQualifiedNameProvider
import hu.bme.mit.semantifyr.oxsts.lang.serializer.ExpressionSerializer
import kotlinx.serialization.Serializable

@Serializable
data class TraceArgument(
    val parameter: String,
    val value: String,
)

@Serializable
data class TraceEntry(
    val self: String,
    val calledTransition: String,
    val arguments: List<TraceArgument>,
    val innerTraces: List<TraceEntry>?,
)

@Serializable
data class CallTraceStep(
    val traces: List<TraceEntry>,
)

@Serializable
data class CallTrace(
    val initialStep: CallTraceStep,
    val steps: List<CallTraceStep>,
)

@Serializable
data class WitnessStateValue(
    val variable: String,
    val value: String,
)

@Serializable
data class WitnessStateStep(
    val values: List<WitnessStateValue>,
)

@Serializable
data class WitnessState(
    val initialStep: WitnessStateStep,
    val steps: List<WitnessStateStep>,
)

class CallTraceTransformer @Inject constructor(
    private val oxstsQualifiedNameProvider: OxstsQualifiedNameProvider,
    private val expressionEvaluationSerializer: ExpressionEvaluationSerializer,
    private val expressionSerializer: ExpressionSerializer,
) {

    fun transformCallTrace(
        classWitness: ClassWitness,
        traces: TransitionCallTraceMap,
    ): CallTrace {
        return CallTrace(
            transformCallTraceStep(classWitness.initialState, traces),
            classWitness.transitionStates.map {
                transformCallTraceStep(it, traces)
            },
        )
    }

    fun transformWitnessState(classWitness: ClassWitness): WitnessState {
        return WitnessState(
            transformWitnessStateStep(classWitness.initialState),
            classWitness.transitionStates.map {
                transformWitnessStateStep(it)
            },
        )
    }

    private fun transformCallTraceStep(
        state: ClassWitnessState,
        traces: TransitionCallTraceMap,
    ): CallTraceStep {
        val activatedTraces = state.activatedTraces.map {
            traces.getTransitionCallTrace(it.variable)
        }
        return CallTraceStep(buildForest(activatedTraces))
    }

    private fun transformWitnessStateStep(state: ClassWitnessState): WitnessStateStep {
        return WitnessStateStep(
            state.values.map {
                transformStateValue(it)
            },
        )
    }

    private fun buildForest(activatedTraces: List<TransitionCallTrace>): List<TraceEntry> {
        val activatedSet = activatedTraces.toSet()
        val childrenByParent = activatedTraces.groupBy {
            it.parent.takeIf { parent -> parent in activatedSet }
        }

        fun build(trace: TransitionCallTrace): TraceEntry {
            val children = childrenByParent[trace].orEmpty().map {
                build(it)
            }
            return TraceEntry(
                self = expressionEvaluationSerializer.serialize(trace.self),
                calledTransition = oxstsQualifiedNameProvider.getFullyQualifiedNameString(trace.transitionDeclaration),
                arguments = trace.arguments.map {
                    TraceArgument(
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

    private fun transformStateValue(value: ClassWitnessStateValue): WitnessStateValue {
        return WitnessStateValue(
            expressionSerializer.serialize(value.variableReference),
            expressionSerializer.serialize(value.value),
        )
    }

}
