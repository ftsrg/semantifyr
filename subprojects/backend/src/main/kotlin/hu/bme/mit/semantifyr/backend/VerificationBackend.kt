/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backend

import hu.bme.mit.semantifyr.backend.witness.InlinedOxstsAssumptionWitness
import hu.bme.mit.semantifyr.compiler.pipeline.context.FlattenedCompilationContext
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ClassDeclaration
import kotlinx.serialization.Serializable
import java.nio.file.Path
import kotlin.time.Duration
import kotlin.time.Instant

class BackendUnsupportedException(message: String, cause: Throwable? = null) : Exception(message, cause)

data class VerificationRequest(
    val verificationCase: VerificationCase,
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
) {
    // kotlin.time.Duration is a value class - its `toIsoString` and component getters are
    // name-mangled by the JVM and unreachable from Java. The Java consumers in the LSP wire
    // layer need ISO 8601 strings to match the rest of the project's duration serialisation
    // (admin endpoint, etc.); these accessors do the bridging.
    fun preparationDurationIso(): String = preparationDuration.toIsoString()
    fun verificationDurationIso(): String = verificationDuration.toIsoString()
    fun backAnnotationDurationIso(): String = backAnnotationDuration.toIsoString()
    fun totalDurationIso(): String = totalDuration.toIsoString()
}

data class BackendVerificationResult(
    val verdict: BackendVerificationVerdict,
    val metadata: VerificationRunMetadata,
    val metrics: VerificationMetrics = VerificationMetrics(),
    val witness: InlinedOxstsAssumptionWitness? = null,
    val message: String? = null,
) {
    val isDecisive = verdict.isDecisive

    companion object {
        fun errored(
            metadata: VerificationRunMetadata,
            metrics: VerificationMetrics,
            message: String,
        ): BackendVerificationResult = BackendVerificationResult(
            verdict = BackendVerificationVerdict.Errored,
            metadata = metadata,
            metrics = metrics,
            message = message,
        )

        fun notSupported(
            metadata: VerificationRunMetadata,
            metrics: VerificationMetrics,
            message: String,
        ): BackendVerificationResult = BackendVerificationResult(
            verdict = BackendVerificationVerdict.NotSupported,
            metadata = metadata,
            metrics = metrics,
            message = message,
        )
    }
}

enum class BackendVerificationVerdict(
    val isDecisive: Boolean,
) {
    Passed(true),
    Failed(true),
    Errored(false),
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
