/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backend

import hu.bme.mit.semantifyr.backend.witness.InlinedOxstsAssumptionWitness
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

data class VerificationCase(
    val classDeclaration: ClassDeclaration,
    val qualifiedName: String,
    val tags: Set<String> = emptySet(),
) {
    val directoryName
        get() = qualifiedNameToDirectoryName(qualifiedName)

    override fun toString(): String {
        val tagSuffix = if (tags.isEmpty()) {
            ""
        } else {
            "@[${tags.joinToString(",")}]"
        }
        return "$qualifiedName$tagSuffix"
    }
}

private val NON_FS_SAFE = Regex("[^A-Za-z0-9._-]")

fun qualifiedNameToDirectoryName(qualifiedName: String): String {
    return qualifiedName.replace("::", ".").replace(NON_FS_SAFE, "_")
}

@Serializable
data class VerificationMetrics(
    val preparationDuration: Duration = Duration.ZERO,
    val verificationDuration: Duration = Duration.ZERO,
    val backAnnotationDuration: Duration = Duration.ZERO,
    val totalDuration: Duration = Duration.ZERO,
)

data class BackendVerificationResult(
    val verdict: VerificationVerdict,
    val metadata: VerificationRunMetadata,
    val metrics: VerificationMetrics = VerificationMetrics(),
    val witness: InlinedOxstsAssumptionWitness? = null,
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
        ): BackendVerificationResult = BackendVerificationResult(
            verdict = VerificationVerdict.Inconclusive,
            metadata = metadata,
            metrics = metrics,
            message = message,
        )

        fun errored(
            metadata: VerificationRunMetadata,
            metrics: VerificationMetrics,
            message: String,
        ): BackendVerificationResult = BackendVerificationResult(
            verdict = VerificationVerdict.Errored,
            metadata = metadata,
            metrics = metrics,
            message = message,
        )

        fun notSupported(
            metadata: VerificationRunMetadata,
            metrics: VerificationMetrics,
            message: String,
        ): BackendVerificationResult = BackendVerificationResult(
            verdict = VerificationVerdict.NotSupported,
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
    NotSupported(false),
}

abstract class VerificationBackend<T : Any> {
    abstract val id: String

    abstract suspend fun verify(
        config: T,
        request: VerificationRequest,
        environment: ExecutionEnvironment,
    ): BackendVerificationResult

    abstract fun probeAvailability(
        config: T,
        environment: ExecutionEnvironment = ExecutionEnvironment.Empty,
    ): AvailabilityReport
}
