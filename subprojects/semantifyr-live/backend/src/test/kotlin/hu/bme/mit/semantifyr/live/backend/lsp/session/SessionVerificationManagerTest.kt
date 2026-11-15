/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.lsp.session

import hu.bme.mit.semantifyr.compiler.pipeline.context.CompilationMetrics
import hu.bme.mit.semantifyr.lang.ide.server.wire.VerificationCaseRequest
import hu.bme.mit.semantifyr.lang.ide.server.wire.VerificationCaseResult
import hu.bme.mit.semantifyr.lang.ide.server.wire.VerificationMetrics
import hu.bme.mit.semantifyr.lang.ide.server.wire.VerificationStatus
import hu.bme.mit.semantifyr.lang.ide.server.wire.VerificationTrace
import hu.bme.mit.semantifyr.lang.ide.server.wire.WitnessValidationResult
import hu.bme.mit.semantifyr.lang.ide.server.wire.WitnessValidationStatus
import hu.bme.mit.semantifyr.live.backend.BackendConfig
import hu.bme.mit.semantifyr.live.backend.VerificationConfig
import hu.bme.mit.semantifyr.live.backend.data.VerificationKind
import hu.bme.mit.semantifyr.live.backend.lsp.service.SessionLanguageClient
import hu.bme.mit.semantifyr.live.backend.lsp.service.SharedExecutorProvider
import hu.bme.mit.semantifyr.live.backend.testing.LspWire
import hu.bme.mit.semantifyr.verifier.CompilationMetricsDto
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.future.await
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds

class SessionVerificationManagerTest {

    private val documentUri = "file:///workspace/snippet.oxsts"

    private val witnessUri = "file:///workspace/.artifacts/snippet.CaseA/witness.oxsts"

    private val executorProvider = SharedExecutorProvider()

    private val registry = SimpleMeterRegistry()

    private val manager = SessionVerificationManager(
        BackendConfig(verification = VerificationConfig(concurrency = 1)),
        VerificationManager(BackendConfig(verification = VerificationConfig(concurrency = 1)), SimpleMeterRegistry()),
        mock<SessionContext> {
            on { sessionId } doReturn "session"
        },
        SessionRunContext(executorProvider),
        SessionClient().apply {
            attach(mock<SessionLanguageClient>())
        },
        registry,
    )

    @AfterEach
    fun closeExecutor() {
        executorProvider.close()
    }

    private suspend fun launch(
        kind: VerificationKind,
        uri: String,
        line: Int,
        work: suspend () -> Any?,
    ): CompletableFuture<Any?> {
        return withSessionScope {
            manager.launch(VerificationCaseRequest(uri, LspWire.range(startLine = line, endLine = line), "smart"), kind, work)
        }
    }

    private fun result(kind: VerificationKind): Any {
        return when (kind) {
            VerificationKind.Verify -> VerificationCaseResult(VerificationStatus.PASSED, null, "theta", "smart", null, null)
            VerificationKind.Validate -> WitnessValidationResult(WitnessValidationStatus.VALID, VerificationStatus.FAILED, null, "theta", "smart", null)
        }
    }

    private suspend fun occupyGate(): CompletableDeferred<Unit> {
        val running = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        launch(VerificationKind.Verify, documentUri, 100) {
            running.complete(Unit)
            release.await()
            result(VerificationKind.Verify)
        }
        running.await()
        return release
    }

    private fun assertSuperseded(kind: VerificationKind, result: Any?) {
        when (kind) {
            VerificationKind.Verify -> {
                val verification = result as VerificationCaseResult
                assertThat(verification.status()).isEqualTo(VerificationStatus.CANCELLED)
                assertThat(verification.message()).contains("superseded")
            }
            VerificationKind.Validate -> {
                val validation = result as WitnessValidationResult
                assertThat(validation.status()).isEqualTo(WitnessValidationStatus.CANCELLED)
                assertThat(validation.message()).contains("superseded")
            }
        }
    }

    @ParameterizedTest
    @EnumSource(VerificationKind::class)
    suspend fun `a queued request is superseded by a newer one of the same kind and location`(kind: VerificationKind) {
        val release = occupyGate()
        val runs = AtomicInteger()
        val older = launch(kind, documentUri, 1) {
            runs.incrementAndGet()
            result(kind)
        }
        val newer = launch(kind, documentUri, 1) {
            runs.incrementAndGet()
            result(kind)
        }

        assertSuperseded(kind, older.await())
        release.complete(Unit)
        assertThat(newer.await()).isEqualTo(result(kind))
        assertThat(runs.get()).isEqualTo(1)
    }

    @Test
    suspend fun `queued requests for other cases are kept`() {
        val release = occupyGate()
        val first = launch(VerificationKind.Verify, documentUri, 1) {
            result(VerificationKind.Verify)
        }
        val second = launch(VerificationKind.Verify, documentUri, 2) {
            result(VerificationKind.Verify)
        }

        release.complete(Unit)
        assertThat(first.await()).isEqualTo(result(VerificationKind.Verify))
        assertThat(second.await()).isEqualTo(result(VerificationKind.Verify))
    }

    @Test
    suspend fun `a running request is never superseded`() {
        val running = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val older = launch(VerificationKind.Verify, documentUri, 1) {
            running.complete(Unit)
            release.await()
            result(VerificationKind.Verify)
        }
        running.await()
        val newer = launch(VerificationKind.Verify, documentUri, 1) {
            result(VerificationKind.Verify)
        }

        release.complete(Unit)
        assertThat(older.await()).isEqualTo(result(VerificationKind.Verify))
        assertThat(newer.await()).isEqualTo(result(VerificationKind.Verify))
    }

    @Test
    suspend fun `a verify drops the queued validation that names the case`() {
        val release = occupyGate()
        val validation = launch(VerificationKind.Validate, documentUri, 1) {
            result(VerificationKind.Validate)
        }
        val verification = launch(VerificationKind.Verify, documentUri, 1) {
            result(VerificationKind.Verify)
        }

        assertSuperseded(VerificationKind.Validate, validation.await())
        release.complete(Unit)
        assertThat(verification.await()).isEqualTo(result(VerificationKind.Verify))
    }

    @Test
    suspend fun `a verify drops the queued validation of the witness its case left`() {
        val witnessResult = VerificationCaseResult(VerificationStatus.FAILED, null, "theta", "smart", null, VerificationTrace(null, null, witnessUri))
        launch(VerificationKind.Verify, documentUri, 1) {
            witnessResult
        }.await()
        val release = occupyGate()
        val validation = launch(VerificationKind.Validate, witnessUri, 0) {
            result(VerificationKind.Validate)
        }
        val otherCase = launch(VerificationKind.Verify, documentUri, 2) {
            result(VerificationKind.Verify)
        }
        val verification = launch(VerificationKind.Verify, documentUri, 1) {
            result(VerificationKind.Verify)
        }

        assertSuperseded(VerificationKind.Validate, validation.await())
        release.complete(Unit)
        assertThat(otherCase.await()).isEqualTo(result(VerificationKind.Verify))
        assertThat(verification.await()).isEqualTo(result(VerificationKind.Verify))
    }

    @Test
    suspend fun `a validation does not drop a queued verify`() {
        val release = occupyGate()
        val verification = launch(VerificationKind.Verify, documentUri, 1) {
            result(VerificationKind.Verify)
        }
        val validation = launch(VerificationKind.Validate, documentUri, 1) {
            result(VerificationKind.Validate)
        }

        release.complete(Unit)
        assertThat(verification.await()).isEqualTo(result(VerificationKind.Verify))
        assertThat(validation.await()).isEqualTo(result(VerificationKind.Validate))
    }

    @Test
    suspend fun `a finished verification records the time of each phase by backend`() {
        val compilation = CompilationMetricsDto.fromMetrics(
            CompilationMetrics(
                createDuration = 1.milliseconds,
                instantiationDuration = 2.milliseconds,
                inliningDuration = 3.milliseconds,
                flatteningDuration = 4.milliseconds,
            ),
        )
        val metrics = VerificationMetrics(
            Duration.ofMillis(100),
            Duration.ofMillis(30),
            Duration.ofMillis(50),
            Duration.ofMillis(20),
            compilation,
        )
        val result = VerificationCaseResult(VerificationStatus.PASSED, null, "theta", "smart", metrics, null)

        launch(VerificationKind.Verify, documentUri, 1) {
            result
        }.await()

        val phaseMilliseconds = { phase: String ->
            registry.get("semantifyr.live.verification.phase")
                .tags("kind", "verify", "backend", "theta", "phase", phase)
                .timer()
                .totalTime(TimeUnit.MILLISECONDS)
        }
        assertThat(phaseMilliseconds("instantiation")).isEqualTo(2.0)
        assertThat(phaseMilliseconds("flattening")).isEqualTo(4.0)
        assertThat(phaseMilliseconds("preparation")).isEqualTo(20.0)
        assertThat(phaseMilliseconds("engine")).isEqualTo(50.0)
        assertThat(phaseMilliseconds("back_annotation")).isEqualTo(20.0)
    }

    @Test
    suspend fun `a timed out verification records no phases`() {
        launch(VerificationKind.Verify, documentUri, 1) {
            VerificationCaseResult.timedOut("timeout", "smart")
        }.await()

        assertThat(registry.find("semantifyr.live.verification.phase").timers()).isEmpty()
    }
}
