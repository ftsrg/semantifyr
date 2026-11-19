/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.verifier

import com.google.inject.Injector
import hu.bme.mit.semantifyr.backend.BackendCapabilityChecker
import hu.bme.mit.semantifyr.backend.BackendConfig
import hu.bme.mit.semantifyr.backend.BackendVerificationRequest
import hu.bme.mit.semantifyr.backend.BackendVerificationResult
import hu.bme.mit.semantifyr.backend.SoundnessDirection
import hu.bme.mit.semantifyr.backend.VerificationBackend
import hu.bme.mit.semantifyr.backend.VerificationMetadata
import hu.bme.mit.semantifyr.backend.VerificationVerdict
import hu.bme.mit.semantifyr.backend.execution.BackendExecutor
import hu.bme.mit.semantifyr.backend.execution.ExecutionEnvironment
import hu.bme.mit.semantifyr.backend.execution.ExecutorKey
import hu.bme.mit.semantifyr.compiler.pipeline.context.TransitionCallTraceMap
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationConfig
import hu.bme.mit.semantifyr.compiler.pipeline.utils.OxstsFactory
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ClassDeclaration
import hu.bme.mit.semantifyr.verifier.portfolio.ConcurrencyGate
import kotlinx.coroutines.delay
import org.mockito.kotlin.mock
import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

fun fakeCase(qualifiedName: String = "pkg.Foo", vararg tags: String): VerificationCase {
    return VerificationCase(
        classDeclaration = mock<ClassDeclaration>(),
        qualifiedName = qualifiedName,
        tags = tags.toSet(),
    )
}

val noopGate: ConcurrencyGate = object : ConcurrencyGate {
    override val slots = Int.MAX_VALUE

    override suspend fun <T> withPermit(block: suspend () -> T): T {
        return block()
    }
}

val noReadBack: suspend (BackendVerificationResult) -> BackendVerificationResult = {
    it
}

fun fakeRequest(): BackendVerificationRequest {
    return BackendVerificationRequest(
        inlinedOxsts = OxstsFactory.createInlinedOxsts().also {
            it.property = OxstsFactory.createPropertyDeclaration().also { declaration ->
                declaration.expression = OxstsFactory.createAG()
            }
        },
        artifactOutputPath = Paths.get(System.getProperty("java.io.tmpdir")),
        transitionCallTraces = TransitionCallTraceMap(emptyMap()),
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
    message: String? = null,
): VerificationResult {
    return VerificationResult(
        metadata = fakeMetadata(backendId),
        verdict = verdict,
        metrics = metrics,
        message = message,
    )
}

@JvmOverloads
fun fakeVerificationReport(
    verdict: VerificationVerdict = VerificationVerdict.Passed,
    backendId: String? = "test",
    kind: ReportKind = ReportKind.Run,
    metrics: VerificationMetrics = VerificationMetrics(),
    trace: ReportTrace? = null,
): VerificationReport {
    return VerificationReport(
        version = REPORT_VERSION,
        kind = kind,
        verificationCase = "pkg::Foo",
        portfolioId = "test",
        optimization = OptimizationConfig.ALL,
        timeout = 1.minutes,
        result = fakeVerificationResult(verdict, backendId, metrics),
        trace = trace,
    )
}

fun fakeVerificationOutcome(verdict: VerificationVerdict = VerificationVerdict.Passed): VerificationOutcome {
    return VerificationOutcome(fakeVerificationReport(verdict), Paths.get("report.json"), null)
}

class AlwaysAvailableExecutor : BackendExecutor {
    override fun isAvailable(): Boolean = true
}

val FakeExecutorKey = ExecutorKey<AlwaysAvailableExecutor>(name = "fake") {
    AlwaysAvailableExecutor()
}

data class FakeConfig(
    override val id: String = "fake",
    override val soundness: SoundnessDirection = SoundnessDirection.Exact,
) : BackendConfig

class FakeBackend(
    override val id: String,
    private val behavior: suspend () -> BackendVerificationResult,
) : VerificationBackend<FakeConfig>() {

    override fun capabilityChecker(parentInjector: Injector, config: FakeConfig): BackendCapabilityChecker {
        return BackendCapabilityChecker.Unlimited
    }
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
        } catch (cancellation: CancellationException) {
            cancellations.incrementAndGet()
            throw cancellation
        }
    }

    companion object {
        private val invariant = OxstsFactory.createAG()

        fun verdict(
            id: String,
            verdict: VerificationVerdict,
            soundness: SoundnessDirection = SoundnessDirection.Exact,
        ): FakeBackend {
            return FakeBackend(id) {
                result(id, verdict, soundness)
            }
        }

        fun delayed(
            id: String,
            delayMillis: Long,
            verdict: VerificationVerdict,
            soundness: SoundnessDirection = SoundnessDirection.Exact,
        ): FakeBackend {
            return FakeBackend(id) {
                delay(delayMillis)
                result(id, verdict, soundness)
            }
        }

        private fun result(
            id: String,
            verdict: VerificationVerdict,
            soundness: SoundnessDirection,
        ): BackendVerificationResult {
            return BackendVerificationResult(
                verdict = verdict,
                metadata = fakeMetadata(id),
                soundness = soundness,
                isTrusted = soundness.trusts(verdict, invariant),
            )
        }

        fun throwing(id: String, throwable: Throwable): FakeBackend {
            return FakeBackend(id) {
                throw throwable
            }
        }
    }
}
