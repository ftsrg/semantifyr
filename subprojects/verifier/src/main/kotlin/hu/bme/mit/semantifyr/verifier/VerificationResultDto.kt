/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.verifier

import hu.bme.mit.semantifyr.backend.BackendMetrics
import hu.bme.mit.semantifyr.backend.VerificationMetadata
import hu.bme.mit.semantifyr.backend.VerificationVerdict
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.verifier.witness.CallTrace
import hu.bme.mit.semantifyr.verifier.witness.CallTraceStep
import hu.bme.mit.semantifyr.verifier.witness.TraceArgument
import hu.bme.mit.semantifyr.verifier.witness.TraceEntry
import hu.bme.mit.semantifyr.verifier.witness.WitnessState
import hu.bme.mit.semantifyr.verifier.witness.WitnessStateStep
import hu.bme.mit.semantifyr.verifier.witness.WitnessStateValue
import java.time.Duration
import java.time.Instant
import kotlin.time.toJavaDuration
import kotlin.time.toJavaInstant

data class VerificationMetadataDto(
    val backendId: String?,
    val startedAt: Instant,
) {
    companion object {
        fun fromMetadata(metadata: VerificationMetadata): VerificationMetadataDto {
            return VerificationMetadataDto(
                backendId = metadata.backendId,
                startedAt = metadata.startedAt.toJavaInstant(),
            )
        }
    }
}

data class BackendMetricsDto(
    val preparationDuration: Duration,
    val verificationDuration: Duration,
    val backAnnotationDuration: Duration,
) {
    companion object {
        fun fromMetrics(metrics: BackendMetrics): BackendMetricsDto {
            return BackendMetricsDto(
                preparationDuration = metrics.preparationDuration.toJavaDuration(),
                verificationDuration = metrics.verificationDuration.toJavaDuration(),
                backAnnotationDuration = metrics.backAnnotationDuration.toJavaDuration(),
            )
        }
    }
}

data class VerifierMetricsDto(
    val compilationDuration: Duration,
    val backAnnotationDuration: Duration,
    val portfolioDuration: Duration,
) {
    companion object {
        fun fromMetrics(metrics: VerifierMetrics): VerifierMetricsDto {
            return VerifierMetricsDto(
                compilationDuration = metrics.compilationDuration.toJavaDuration(),
                backAnnotationDuration = metrics.backAnnotationDuration.toJavaDuration(),
                portfolioDuration = metrics.portfolioDuration.toJavaDuration(),
            )
        }
    }
}

data class VerificationMetricsDto(
    val totalDuration: Duration,
    val backend: BackendMetricsDto?,
    val verifier: VerifierMetricsDto,
) {
    companion object {
        fun fromMetrics(result: VerificationMetrics): VerificationMetricsDto {
            return VerificationMetricsDto(
                totalDuration = result.totalDuration.toJavaDuration(),
                backend = result.backend?.let { BackendMetricsDto.fromMetrics(it) },
                verifier = VerifierMetricsDto.fromMetrics(result.verifier),
            )
        }
    }
}

data class TraceArgumentDto(
    val parameter: String,
    val value: String,
) {
    companion object {
        fun fromTraceArgument(argument: TraceArgument): TraceArgumentDto {
            return TraceArgumentDto(
                parameter = argument.parameter,
                value = argument.value,
            )
        }
    }
}

data class TraceEntryDto(
    val self: String,
    val calledTransition: String,
    val arguments: List<TraceArgumentDto>,
    val innerTraces: List<TraceEntryDto>?,
) {
    companion object {
        fun fromTraceEntry(entry: TraceEntry): TraceEntryDto {
            return TraceEntryDto(
                self = entry.self,
                calledTransition = entry.calledTransition,
                arguments = entry.arguments.map { TraceArgumentDto.fromTraceArgument(it) },
                innerTraces = entry.innerTraces?.map { fromTraceEntry(it) },
            )
        }
    }
}

data class CallTraceStepDto(
    val traces: List<TraceEntryDto>,
) {
    companion object {
        fun fromCallTraceStep(step: CallTraceStep): CallTraceStepDto {
            return CallTraceStepDto(
                traces = step.traces.map { TraceEntryDto.fromTraceEntry(it) },
            )
        }
    }
}

data class CallTraceDto(
    val initialStep: CallTraceStepDto,
    val steps: List<CallTraceStepDto>,
) {
    companion object {
        fun fromCallTrace(callTrace: CallTrace): CallTraceDto {
            return CallTraceDto(
                initialStep = CallTraceStepDto.fromCallTraceStep(callTrace.initialStep),
                steps = callTrace.steps.map { CallTraceStepDto.fromCallTraceStep(it) },
            )
        }
    }
}

data class WitnessStateValueDto(
    val variable: String,
    val value: String,
) {
    companion object {
        fun fromWitnessStateValue(value: WitnessStateValue): WitnessStateValueDto {
            return WitnessStateValueDto(
                variable = value.variable,
                value = value.value,
            )
        }
    }
}

data class WitnessStateStepDto(
    val values: List<WitnessStateValueDto>,
) {
    companion object {
        fun fromWitnessStateStep(step: WitnessStateStep): WitnessStateStepDto {
            return WitnessStateStepDto(
                values = step.values.map { WitnessStateValueDto.fromWitnessStateValue(it) },
            )
        }
    }
}

data class WitnessStateDto(
    val initialStep: WitnessStateStepDto,
    val steps: List<WitnessStateStepDto>,
) {
    companion object {
        fun fromWitnessState(state: WitnessState): WitnessStateDto {
            return WitnessStateDto(
                initialStep = WitnessStateStepDto.fromWitnessStateStep(state.initialStep),
                steps = state.steps.map { WitnessStateStepDto.fromWitnessStateStep(it) },
            )
        }
    }
}

data class TraceDto(
    val backAnnotatedModel: InlinedOxsts,
    val witnessState: WitnessStateDto,
    val callTrace: CallTraceDto,
) {
    companion object {
        fun fromTrace(trace: Trace): TraceDto {
            return TraceDto(
                backAnnotatedModel = trace.backAnnotatedModel,
                witnessState = WitnessStateDto.fromWitnessState(trace.witnessState),
                callTrace = CallTraceDto.fromCallTrace(trace.callTrace),
            )
        }
    }
}

data class VerificationResultDto(
    val metadata: VerificationMetadataDto,
    val verdict: VerificationVerdict,
    val metrics: VerificationMetricsDto,
    val message: String?,
    val trace: TraceDto?,
) {
    companion object {
        fun fromResult(result: VerificationResult): VerificationResultDto {
            return VerificationResultDto(
                metadata = VerificationMetadataDto.fromMetadata(result.metadata),
                verdict = result.verdict,
                metrics = VerificationMetricsDto.fromMetrics(result.metrics),
                message = result.message,
                trace = result.trace?.let { TraceDto.fromTrace(it) },
            )
        }
    }
}

data class WitnessValidationResultDto(
    val kind: Kind,
    val verification: VerificationResultDto,
) {
    enum class Kind {
        VALID,
        INVALID,
        INCONCLUSIVE,
        ERRORED,
    }

    companion object {
        fun fromResult(result: hu.bme.mit.semantifyr.verifier.witness.WitnessValidationResult): WitnessValidationResultDto {
            val kind = when (result) {
                is hu.bme.mit.semantifyr.verifier.witness.WitnessValidationResult.Valid -> Kind.VALID
                is hu.bme.mit.semantifyr.verifier.witness.WitnessValidationResult.Invalid -> Kind.INVALID
                is hu.bme.mit.semantifyr.verifier.witness.WitnessValidationResult.Inconclusive -> Kind.INCONCLUSIVE
                is hu.bme.mit.semantifyr.verifier.witness.WitnessValidationResult.Errored -> Kind.ERRORED
            }
            return WitnessValidationResultDto(kind, result.verification.toJavaDto())
        }
    }
}

fun VerificationResult.toJavaDto(): VerificationResultDto {
    return VerificationResultDto.fromResult(this)
}

fun hu.bme.mit.semantifyr.verifier.witness.WitnessValidationResult.toJavaDto(): WitnessValidationResultDto {
    return WitnessValidationResultDto.fromResult(this)
}

fun Trace.toJavaDto(): TraceDto {
    return TraceDto.fromTrace(this)
}

fun VerificationMetrics.toJavaDto(): VerificationMetricsDto {
    return VerificationMetricsDto.fromMetrics(this)
}

fun VerifierMetrics.toJavaDto(): VerifierMetricsDto {
    return VerifierMetricsDto.fromMetrics(this)
}

fun BackendMetrics.toJavaDto(): BackendMetricsDto {
    return BackendMetricsDto.fromMetrics(this)
}

fun VerificationMetadata.toJavaDto(): VerificationMetadataDto {
    return VerificationMetadataDto.fromMetadata(this)
}

fun CallTrace.toJavaDto(): CallTraceDto {
    return CallTraceDto.fromCallTrace(this)
}

fun WitnessState.toJavaDto(): WitnessStateDto {
    return WitnessStateDto.fromWitnessState(this)
}
