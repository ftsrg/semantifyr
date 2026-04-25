/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backend

import hu.bme.mit.semantifyr.backend.witness.OxstsClassAssumptionWitness
import hu.bme.mit.semantifyr.backend.witness.SerializableTraceData
import hu.bme.mit.semantifyr.compiler.pipeline.context.FlattenedCompilationContext
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ClassDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import kotlinx.serialization.Serializable
import java.nio.file.Path
import kotlin.time.Duration
import kotlin.time.Instant

data class VerificationRequest(
    val case: VerificationCase,
    val input: InlinedOxsts,
    val compilation: FlattenedCompilationContext,
    val artifactOutputPath: Path,
)

@Serializable
data class VerificationRunMetadata(
    val backendId: String,
    val startedAt: Instant,
    val caseQualifiedName: String,
)

sealed interface VerificationTrace {
    data object NoTrace : VerificationTrace

    data class OxstsWitness(
        val classWitness: OxstsClassAssumptionWitness,
        val backAnnotatedWitness: InlinedOxsts,
        val callTrace: SerializableTraceData,
    ) : VerificationTrace
}

data class VerificationCase(
    val classDeclaration: ClassDeclaration,
    val qualifiedName: String,
    val tags: Set<String> = emptySet(),
) {
    override fun toString(): String {
        val tagSuffix = if (tags.isEmpty()) {
            ""
        } else {
            "@[${tags.joinToString(",")}]"
        }
        return "$qualifiedName$tagSuffix"
    }
}

@Serializable
data class VerificationMetrics(
    val preparationDuration: Duration = Duration.ZERO,
    val verificationDuration: Duration = Duration.ZERO,
    val backAnnotationDuration: Duration = Duration.ZERO,
    val totalDuration: Duration = Duration.ZERO,
)

@Serializable
data class VerificationResult(
    val verdict: VerificationVerdict,
    val metadata: VerificationRunMetadata,
    val metrics: VerificationMetrics = VerificationMetrics(),
    val verificationTrace: VerificationTrace = VerificationTrace.NoTrace,
    val message: String? = null,
) {
    val isPassed: Boolean get() = verdict == VerificationVerdict.Passed
    val isFailed: Boolean get() = verdict == VerificationVerdict.Failed
    val isDecisive: Boolean get() = verdict.isDecisive

    companion object {
        fun inconclusive(
            metadata: VerificationRunMetadata,
            metrics: VerificationMetrics,
            message: String,
        ): VerificationResult = VerificationResult(
            verdict = VerificationVerdict.Inconclusive,
            metadata = metadata,
            metrics = metrics,
            message = message,
        )

        fun errored(
            metadata: VerificationRunMetadata,
            metrics: VerificationMetrics,
            message: String,
        ): VerificationResult = VerificationResult(
            verdict = VerificationVerdict.Errored,
            metadata = metadata,
            metrics = metrics,
            message = message,
        )
    }
}

enum class VerificationVerdict(
    val isDecisive: Boolean,
) {
    Passed(true),
    Failed(true),
    Errored(false),
    Inconclusive(false),
}

abstract class VerificationBackend<T : Any> {
    abstract val id: String

    abstract suspend fun verify(
        config: T,
        request: VerificationRequest,
        environment: ExecutionEnvironment,
    ): VerificationResult

    abstract fun probeAvailability(
        config: T,
        environment: ExecutionEnvironment = ExecutionEnvironment.Empty,
    ): AvailabilityReport
}
