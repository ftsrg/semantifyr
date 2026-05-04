/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.verifier.internal

import com.google.inject.Inject
import com.google.inject.Injector
import hu.bme.mit.semantifyr.backend.BackendMetrics
import hu.bme.mit.semantifyr.backend.BackendUnsupportedException
import hu.bme.mit.semantifyr.backend.BackendVerificationRequest
import hu.bme.mit.semantifyr.backend.BackendVerificationResult
import hu.bme.mit.semantifyr.backend.VerificationMetadata
import hu.bme.mit.semantifyr.backend.VerificationVerdict
import hu.bme.mit.semantifyr.backend.execution.AvailabilityReport
import hu.bme.mit.semantifyr.backend.execution.ExecutionEnvironment
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.ArtifactConfig
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationConfig
import hu.bme.mit.semantifyr.compiler.reader.SemantifyrLoader
import hu.bme.mit.semantifyr.oxsts.lang.tests.InjectWithOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ClassDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.OxstsModelPackage
import hu.bme.mit.semantifyr.verifier.ProgressContext
import hu.bme.mit.semantifyr.verifier.SemantifyrVerifier
import hu.bme.mit.semantifyr.verifier.VerificationCase
import hu.bme.mit.semantifyr.verifier.portfolio.ConcurrencyGate
import hu.bme.mit.semantifyr.verifier.portfolio.VerificationPortfolio
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.coroutines.cancellation.CancellationException
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@InjectWithOxsts
class SemantifyrVerifierImplTest {

    @Inject
    private lateinit var injector: Injector

    @Inject
    private lateinit var loader: SemantifyrLoader

    private val modelPath = Path("../../oxsts-test-models/simple/simple.oxsts")

    @Test
    suspend fun `successful portfolio verdict propagates to VerificationResult`(@TempDir output: Path) {
        val backendResult = decisiveResult(VerificationVerdict.Passed, "fake-backend")
        val portfolio = ScriptedPortfolio { _, _ -> backendResult }

        val result = build(portfolio, output, timeout = 5.seconds)
            .verify(loadFirstCase())

        assertThat(result.verdict).isEqualTo(VerificationVerdict.Passed)
        assertThat(result.metadata.backendId).isEqualTo("fake-backend")
        assertThat(portfolio.invocations).isEqualTo(1)
    }

    @Test
    fun `portfolio timeout maps to Inconclusive with a Timeout message`(@TempDir output: Path) = runTest {
        val portfolio = ScriptedPortfolio { _, _ ->
            delay(10.seconds)
            decisiveResult(VerificationVerdict.Passed)
        }

        val result = build(portfolio, output, timeout = 50.milliseconds)
            .verify(loadFirstCase())

        assertThat(result.verdict).isEqualTo(VerificationVerdict.Inconclusive)
        assertThat(result.message).contains("Timeout").contains("exceeded")
        assertThat(result.metadata.backendId).isNull()
    }

    @Test
    suspend fun `BackendUnsupportedException from the portfolio yields NotSupported`(@TempDir output: Path) {
        val portfolio = ScriptedPortfolio { _, _ ->
            throw BackendUnsupportedException("engine cannot represent this model")
        }

        val result = build(portfolio, output)
            .verify(loadFirstCase())

        assertThat(result.verdict).isEqualTo(VerificationVerdict.NotSupported)
        assertThat(result.message).contains("engine cannot represent")
    }

    @Test
    suspend fun `arbitrary exception from the portfolio yields Errored`(@TempDir output: Path) {
        val portfolio = ScriptedPortfolio { _, _ ->
            error("portfolio blew up")
        }

        val result = build(portfolio, output)
            .verify(loadFirstCase())

        assertThat(result.verdict).isEqualTo(VerificationVerdict.Errored)
        assertThat(result.message).contains("portfolio blew up")
    }

    @Test
    fun `CancellationException from the portfolio is rethrown`(@TempDir output: Path) = runTest {
        val portfolio = ScriptedPortfolio { _, _ ->
            throw CancellationException("caller cancelled")
        }

        assertThatThrownBy {
            kotlinx.coroutines.runBlocking {
                build(portfolio, output, timeout = 5.seconds).verify(loadFirstCase())
            }
        }.isInstanceOf(CancellationException::class.java)
    }

    @Test
    suspend fun `metrics record compilation portfolio and total durations`(@TempDir output: Path) {
        val portfolio = ScriptedPortfolio { _, _ ->
            delay(20.milliseconds)
            decisiveResult(VerificationVerdict.Passed)
        }

        val result = build(portfolio, output, timeout = 5.seconds)
            .verify(loadFirstCase())

        assertThat(result.metrics.totalDuration).isGreaterThan(Duration.ZERO)
        assertThat(result.metrics.verifier.compilationDuration).isGreaterThan(Duration.ZERO)
        assertThat(result.metrics.verifier.portfolioDuration).isGreaterThan(Duration.ZERO)
        assertThat(result.metrics.backend).isNotNull
    }

    @Test
    suspend fun `failed metadata sets backend to null when the portfolio errors`(@TempDir output: Path) {
        val portfolio = ScriptedPortfolio { _, _ -> error("boom") }

        val result = build(portfolio, output)
            .verify(loadFirstCase())

        assertThat(result.metrics.backend).isNull()
    }

    @Test
    suspend fun `report json is written to the per-case output directory`(@TempDir output: Path) {
        val portfolio = ScriptedPortfolio { _, _ -> decisiveResult(VerificationVerdict.Passed) }
        val case = loadFirstCase()

        build(portfolio, output, artifacts = ArtifactConfig.ALL).verify(case)

        val caseDir = output.resolve(case.qualifiedName.replace("::", "."))
        val reportFile = caseDir.resolve("report.json")
        assertThat(reportFile.exists()).isTrue
    }

    private fun build(
        portfolio: VerificationPortfolio,
        output: Path,
        timeout: Duration = 5.seconds,
        artifacts: ArtifactConfig = ArtifactConfig.NONE,
    ): SemantifyrVerifier {
        return SemantifyrVerifier.builder()
            .injector(injector)
            .portfolio(portfolio)
            .artifacts(artifacts)
            .outputDirectory(output)
            .timeout(timeout)
            .optimization(OptimizationConfig.NONE)
            .build()
    }

    private fun loadFirstCase(): VerificationCase {
        val context = loader.loadStandaloneModel(modelPath)
        val klass = context.modelResources.asSequence()
            .flatMap { it.contents }
            .filterIsInstance<OxstsModelPackage>()
            .flatMap { it.declarations }
            .filterIsInstance<ClassDeclaration>()
            .first()
        return VerificationCase(
            qualifiedName = "semantifyr::verification::simple::${klass.name}",
            classDeclaration = klass,
        )
    }

    private fun decisiveResult(
        verdict: VerificationVerdict,
        backendId: String = "fake-backend",
    ): BackendVerificationResult {
        return BackendVerificationResult(
            metadata = VerificationMetadata(backendId = backendId, startedAt = Clock.System.now()),
            verdict = verdict,
            metrics = BackendMetrics(),
        )
    }

    private class ScriptedPortfolio(
        private val behavior: suspend (BackendVerificationRequest, ProgressContext) -> BackendVerificationResult,
    ) : VerificationPortfolio() {
        override val id: String = "scripted"
        override val displayName: String = "scripted"
        override val description: String = "test-only"

        var invocations: Int = 0
            private set

        override fun availability(environment: ExecutionEnvironment): AvailabilityReport {
            return AvailabilityReport.Available
        }

        override suspend fun verify(
            parentInjector: Injector,
            request: BackendVerificationRequest,
            gate: ConcurrencyGate,
            environment: ExecutionEnvironment,
            progress: ProgressContext,
        ): BackendVerificationResult {
            invocations++
            return behavior(request, progress)
        }
    }
}
