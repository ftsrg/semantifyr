/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.verifier

import com.google.inject.Injector
import hu.bme.mit.semantifyr.backend.BackendConfig
import hu.bme.mit.semantifyr.backend.BackendVerificationRequest
import hu.bme.mit.semantifyr.backend.BackendVerificationResult
import hu.bme.mit.semantifyr.backend.VerificationBackend
import hu.bme.mit.semantifyr.backend.VerificationMetadata
import hu.bme.mit.semantifyr.backend.VerificationVerdict
import hu.bme.mit.semantifyr.backend.execution.BackendExecutor
import hu.bme.mit.semantifyr.backend.execution.ExecutionEnvironment
import hu.bme.mit.semantifyr.backend.execution.ExecutorKey
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ClassDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.verifier.portfolio.ConcurrencyGate
import kotlinx.coroutines.delay
import org.mockito.kotlin.mock
import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock

fun fakeCase(qualifiedName: String = "pkg.Foo", vararg tags: String): VerificationCase {
    return VerificationCase(
        classDeclaration = mock<ClassDeclaration>(),
        qualifiedName = qualifiedName,
        tags = tags.toSet(),
    )
}

val noopGate: ConcurrencyGate = object : ConcurrencyGate {
    override suspend fun <T> withPermit(block: suspend () -> T): T {
        return block()
    }
}

fun fakeRequest(): BackendVerificationRequest {
    return BackendVerificationRequest(
        inlinedOxsts = mock<InlinedOxsts>(),
        artifactOutputPath = Paths.get(System.getProperty("java.io.tmpdir")),
    )
}

fun fakeMetadata(backendId: String? = "test"): VerificationMetadata {
    return VerificationMetadata(
        backendId = backendId,
        startedAt = Clock.System.now(),
    )
}

fun fakeVerificationResult(
    verdict: VerificationVerdict = VerificationVerdict.Passed,
    backendId: String? = "test",
    metrics: VerificationMetrics = VerificationMetrics(),
    trace: Trace? = null,
    message: String? = null,
): VerificationResult {
    return VerificationResult(
        metadata = fakeMetadata(backendId),
        verdict = verdict,
        metrics = metrics,
        trace = trace,
        message = message,
    )
}

class AlwaysAvailableExecutor : BackendExecutor {
    override fun isAvailable(): Boolean = true
}

val FakeExecutorKey = ExecutorKey<AlwaysAvailableExecutor>(name = "fake") {
    AlwaysAvailableExecutor()
}

data class FakeConfig(override val id: String = "fake") : BackendConfig

class FakeBackend(
    override val id: String,
    private val behavior: suspend () -> BackendVerificationResult,
) : VerificationBackend<FakeConfig>() {
    override val executorKey = FakeExecutorKey

    val invocations = AtomicInteger(0)
    val cancellations = AtomicInteger(0)

    override suspend fun verify(
        parentInjector: Injector,
        config: FakeConfig,
        request: BackendVerificationRequest,
        environment: ExecutionEnvironment,
    ): BackendVerificationResult {
        invocations.incrementAndGet()
        try {
            return behavior()
        } catch (c: CancellationException) {
            cancellations.incrementAndGet()
            throw c
        }
    }

    companion object {
        fun verdict(id: String, verdict: VerificationVerdict): FakeBackend {
            return FakeBackend(id) {
                BackendVerificationResult(verdict = verdict, metadata = fakeMetadata(id))
            }
        }

        fun delayed(
            id: String,
            delayMillis: Long,
            verdict: VerificationVerdict,
        ): FakeBackend {
            return FakeBackend(id) {
                delay(delayMillis)
                BackendVerificationResult(verdict = verdict, metadata = fakeMetadata(id))
            }
        }

        fun throwing(id: String, throwable: Throwable): FakeBackend {
            return FakeBackend(id) {
                throw throwable
            }
        }
    }
}
