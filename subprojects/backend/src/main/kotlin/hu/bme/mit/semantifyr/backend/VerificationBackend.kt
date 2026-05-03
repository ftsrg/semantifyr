/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backend

import com.google.inject.Injector
import hu.bme.mit.semantifyr.backend.execution.BackendExecutor
import hu.bme.mit.semantifyr.backend.execution.ExecutionEnvironment
import hu.bme.mit.semantifyr.backend.execution.ExecutorKey
import hu.bme.mit.semantifyr.backend.witness.InlinedWitness
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import kotlinx.serialization.Serializable
import java.nio.file.Path
import kotlin.time.Duration
import kotlin.time.Instant

class BackendUnsupportedException(message: String, cause: Throwable? = null) : Exception(message, cause)

data class BackendVerificationRequest(
    val inlinedOxsts: InlinedOxsts,
    val artifactOutputPath: Path,
)

@Serializable
data class VerificationMetadata(
    val backendId: String?,
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
    val metadata: VerificationMetadata,
    val metrics: VerificationMetrics = VerificationMetrics(),
    val inlinedWitness: InlinedWitness? = null,
    val message: String? = null,
) {
    val isDecisive = verdict.isDecisive
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

sealed interface AvailabilityReport {
    object Available : AvailabilityReport {
        override val isUsable = true
    }

    data class Unavailable(
        val reason: String,
        val hints: List<String> = emptyList(),
    ) : AvailabilityReport {
        override val isUsable = false
    }

    val isUsable: Boolean
}

abstract class VerificationBackend<T : Any> {
    abstract val id: String

    abstract val executorKey: ExecutorKey<BackendExecutor>

    abstract suspend fun verify(
        parentInjector: Injector,
        config: T,
        request: BackendVerificationRequest,
        environment: ExecutionEnvironment,
    ): BackendVerificationResult
}
