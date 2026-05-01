/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backend

import com.google.inject.Injector
import hu.bme.mit.semantifyr.backend.execution.ExecutionEnvironment
import hu.bme.mit.semantifyr.backend.witness.InlinedWitness
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import kotlinx.serialization.Serializable
import java.nio.file.Path
import kotlin.time.Duration
import kotlin.time.Instant

class BackendUnsupportedException(message: String, cause: Throwable? = null) : Exception(message, cause)

data class VerificationRequest(
    val inlinedOxsts: InlinedOxsts,
    val artifactOutputPath: Path,
)

@Serializable
data class VerificationRunMetadata(
    val backendId: String,
    val startedAt: Instant,
)

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
    val inlinedWitness: InlinedWitness? = null,
    val message: String? = null,
) {
    val isDecisive = verdict.isDecisive

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
    Inconclusive(false),
    Errored(false),
    NotSupported(false),
}

abstract class VerificationBackend<T : Any> {
    abstract val id: String

    abstract suspend fun verify(
        parentInjector: Injector,
        config: T,
        request: VerificationRequest,
        environment: ExecutionEnvironment,
    ): BackendVerificationResult

    abstract fun probeAvailability(
        config: T,
        environment: ExecutionEnvironment = ExecutionEnvironment.Empty,
    ): AvailabilityReport

}
