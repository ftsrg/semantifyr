/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.portfolios

import com.google.inject.Injector
import hu.bme.mit.semantifyr.backend.BackendConfig
import hu.bme.mit.semantifyr.backend.BackendMetrics
import hu.bme.mit.semantifyr.backend.BackendVerificationRequest
import hu.bme.mit.semantifyr.backend.BackendVerificationResult
import hu.bme.mit.semantifyr.backend.VerificationBackend
import hu.bme.mit.semantifyr.backend.VerificationMetadata
import hu.bme.mit.semantifyr.backend.VerificationVerdict
import hu.bme.mit.semantifyr.backend.execution.BackendExecutor
import hu.bme.mit.semantifyr.backend.execution.ExecutionEnvironment
import hu.bme.mit.semantifyr.backend.execution.ExecutorKey
import hu.bme.mit.semantifyr.compiler.pipeline.utils.OxstsFactory
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import kotlinx.coroutines.delay
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock

class FakeExecutor(private val available: Boolean) : BackendExecutor {
    override fun isAvailable(): Boolean {
        return available
    }
}

val AvailableKey: ExecutorKey<BackendExecutor> = ExecutorKey("fake-available") {
    FakeExecutor(true)
}

val UnavailableKey: ExecutorKey<BackendExecutor> = ExecutorKey("fake-unavailable") {
    FakeExecutor(false)
}

class PassingBackend<T : BackendConfig>(
    override val id: String = "passing",
    override val executorKey: ExecutorKey<BackendExecutor> = AvailableKey,
) : VerificationBackend<T>() {

    override suspend fun verify(
        parentInjector: Injector,
        config: T,
        request: BackendVerificationRequest,
        environment: ExecutionEnvironment,
    ): BackendVerificationResult {
        return judgedResult(id, VerificationVerdict.Passed, config, request)
    }
}

class FailingBackend<T : BackendConfig>(
    override val id: String = "failing",
    override val executorKey: ExecutorKey<BackendExecutor> = AvailableKey,
) : VerificationBackend<T>() {

    override suspend fun verify(
        parentInjector: Injector,
        config: T,
        request: BackendVerificationRequest,
        environment: ExecutionEnvironment,
    ): BackendVerificationResult {
        return judgedResult(id, VerificationVerdict.Failed, config, request)
    }
}

class ErroringBackend<T : BackendConfig>(
    override val id: String = "erroring",
    override val executorKey: ExecutorKey<BackendExecutor> = AvailableKey,
    private val message: String = "scripted failure",
) : VerificationBackend<T>() {

    override suspend fun verify(
        parentInjector: Injector,
        config: T,
        request: BackendVerificationRequest,
        environment: ExecutionEnvironment,
    ): BackendVerificationResult {
        error(message)
    }
}

class NotSupportedBackend<T : BackendConfig>(
    override val id: String = "not-supported",
    override val executorKey: ExecutorKey<BackendExecutor> = AvailableKey,
) : VerificationBackend<T>() {

    override suspend fun verify(
        parentInjector: Injector,
        config: T,
        request: BackendVerificationRequest,
        environment: ExecutionEnvironment,
    ): BackendVerificationResult {
        return BackendVerificationResult(
            verdict = VerificationVerdict.NotSupported,
            metadata = VerificationMetadata(id, Clock.System.now()),
            metrics = BackendMetrics(),
        )
    }
}

class SlowBackend<T : BackendConfig>(
    override val id: String = "slow",
    override val executorKey: ExecutorKey<BackendExecutor> = AvailableKey,
    private val delayMillis: Long = 10_000,
    private val verdict: VerificationVerdict = VerificationVerdict.Passed,
) : VerificationBackend<T>() {

    val invocations = AtomicInteger(0)
    val cancellations = AtomicInteger(0)

    override suspend fun verify(
        parentInjector: Injector,
        config: T,
        request: BackendVerificationRequest,
        environment: ExecutionEnvironment,
    ): BackendVerificationResult {
        invocations.incrementAndGet()
        try {
            delay(delayMillis)
        } catch (c: CancellationException) {
            cancellations.incrementAndGet()
            throw c
        }
        return judgedResult(id, verdict, config, request)
    }
}

private fun judgedResult(
    id: String,
    verdict: VerificationVerdict,
    config: BackendConfig,
    request: BackendVerificationRequest,
): BackendVerificationResult {
    return BackendVerificationResult(
        verdict = verdict,
        metadata = VerificationMetadata(id, Clock.System.now()),
        metrics = BackendMetrics(),
        soundness = config.soundness,
        isTrusted = config.soundness.trusts(verdict, request.inlinedOxsts.property.expression),
    )
}

fun stubRequest(
    outputDirectory: Path,
    property: Expression = OxstsFactory.createAG(),
): BackendVerificationRequest {
    val inlinedOxsts = OxstsFactory.createInlinedOxsts().also {
        it.property = OxstsFactory.createPropertyDeclaration().also { declaration ->
            declaration.expression = property
        }
    }
    return BackendVerificationRequest(
        inlinedOxsts = inlinedOxsts,
        artifactOutputPath = outputDirectory,
    )
}
