/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.benchmark.verification

import com.google.inject.Inject
import com.google.inject.Injector
import hu.bme.mit.semantifyr.backend.BackendVerificationRequest
import hu.bme.mit.semantifyr.backend.BackendVerificationResult
import hu.bme.mit.semantifyr.backend.VerificationVerdict
import hu.bme.mit.semantifyr.backend.execution.AvailabilityReport
import hu.bme.mit.semantifyr.backend.execution.ExecutionEnvironment
import hu.bme.mit.semantifyr.backend.execution.ExecutorKey
import hu.bme.mit.semantifyr.benchmark.BenchmarkRecord
import hu.bme.mit.semantifyr.benchmark.CampaignRunner
import hu.bme.mit.semantifyr.benchmark.Outcome
import hu.bme.mit.semantifyr.benchmark.ResultStore
import hu.bme.mit.semantifyr.benchmark.campaign
import hu.bme.mit.semantifyr.compiler.reader.SemantifyrLoader
import hu.bme.mit.semantifyr.oxsts.lang.tests.InjectWithOxsts
import hu.bme.mit.semantifyr.verifier.FakeBackend
import hu.bme.mit.semantifyr.verifier.FakeConfig
import hu.bme.mit.semantifyr.verifier.ProgressContext
import hu.bme.mit.semantifyr.verifier.discovery.VerificationCaseDiscoverer
import hu.bme.mit.semantifyr.verifier.portfolio.ConcurrencyGate
import hu.bme.mit.semantifyr.verifier.portfolio.SingleBackendPortfolio
import hu.bme.mit.semantifyr.verifier.portfolio.VerificationPortfolio
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.emf.ecore.resource.ResourceSet
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.time.Duration.Companion.seconds

@InjectWithOxsts
class VerificationCellActionTest {

    @Inject
    private lateinit var injector: Injector

    @Inject
    private lateinit var loader: SemantifyrLoader

    @Inject
    private lateinit var discoverer: VerificationCaseDiscoverer

    @TempDir
    private lateinit var directory: Path

    private inner class SmokeModel : BenchmarkModel {
        var preparations = 0
        var resourceSet: ResourceSet? = null

        override val name = "smoke.oxsts"
        override val cases = listOf(BenchmarkCase("CounterReachesThree", VerificationVerdict.Passed))

        override fun prepare(workDirectory: Path): PreparedModel {
            preparations++
            val context = loader.loadStandaloneModel(Path("../../oxsts-test-models/smoke/smoke.oxsts"))
            resourceSet = context.resourceSet
            return PreparedModel(
                discoverer.discover(context).associateBy {
                    it.qualifiedName.substringAfterLast("::")
                },
            )
        }
    }

    private class ProbedPortfolio(
        private val delegate: VerificationPortfolio,
        private val report: AvailabilityReport = AvailabilityReport.Available,
    ) : VerificationPortfolio() {
        var probes = 0

        override val id = delegate.id
        override val displayName = delegate.displayName
        override val description = delegate.description
        override val executorKeys: Set<ExecutorKey<*>> = delegate.executorKeys

        override fun availability(environment: ExecutionEnvironment): AvailabilityReport {
            probes++
            return report
        }

        override suspend fun verify(
            parentInjector: Injector,
            request: BackendVerificationRequest,
            gate: ConcurrencyGate,
            environment: ExecutionEnvironment,
            progress: ProgressContext,
        ): BackendVerificationResult {
            return delegate.verify(parentInjector, request, gate, environment, progress)
        }
    }

    private fun fakePortfolio(verdict: VerificationVerdict, config: String = "fake"): ProbedPortfolio {
        return ProbedPortfolio(SingleBackendPortfolio(FakeBackend.verdict("fake", verdict), FakeConfig(config)))
    }

    private suspend fun runCampaign(
        model: BenchmarkModel,
        portfolios: List<VerificationPortfolio>,
        levels: List<OptimizationLevel> = listOf(OptimizationLevel.All),
    ): List<BenchmarkRecord> {
        val campaign = campaign("smoke") {
            axis(VerificationAxes.model, listOf(model))
            axis(VerificationAxes.case) {
                it[VerificationAxes.model].cases
            }
            axis(VerificationAxes.backendConfig, portfolios)
            axis(VerificationAxes.optimization, levels)
            repetitions = 1
            warmups = 0
        }
        val action = VerificationCellAction(injector, 10.seconds, ExecutionEnvironment.builder().build(), directory.resolve("models"))
        return CampaignRunner(campaign, ResultStore(directory), action).run()
    }

    @Test
    suspend fun `a record carries the verdict, its judgement and the sizes read from report json`() {
        val record = runCampaign(SmokeModel(), listOf(fakePortfolio(VerificationVerdict.Passed))).single()

        val measurement = record.measurement
        assertThat(measurement.outcome).isEqualTo(Outcome.Completed)
        assertThat(measurement.verdict).isEqualTo(VerificationVerdict.Passed)
        assertThat(measurement.expected).isEqualTo(VerificationVerdict.Passed)
        assertThat(measurement.asExpected).isTrue()
        assertThat(measurement.decidedBy).isEqualTo("fake")
        assertThat(measurement.wallTime).isNotNull()
        assertThat(measurement.cpuTime).isNotNull()
        val compilation = measurement.metrics!!.verifier.compilation
        assertThat(compilation.inputSize.variables).isEqualTo(1)
        assertThat(compilation.inputSize.expressions).isPositive()
        assertThat(compilation.flattenedSize.variables).isEqualTo(1)
        assertThat(compilation.flatteningOptimization.passes).isNotEmpty()
    }

    @Test
    suspend fun `a wrong decisive verdict is judged wrong and an undecided one is not judged`() {
        val records = runCampaign(
            SmokeModel(),
            listOf(fakePortfolio(VerificationVerdict.Failed, "wrong"), fakePortfolio(VerificationVerdict.Inconclusive, "undecided")),
        )

        assertThat(records.map { it.measurement.asExpected }).containsExactly(false, null)
        assertThat(records.map { it.measurement.decidedBy }).containsExactly("fake", null)
    }

    @Test
    suspend fun `the inlined models a cell adds to the shared resource set are unloaded after it`() {
        val model = SmokeModel()

        runCampaign(model, listOf(fakePortfolio(VerificationVerdict.Passed)))

        assertThat(model.resourceSet!!.resources.map { it.uri.lastSegment() }).doesNotContain("inlined.oxsts")
    }

    @Test
    suspend fun `the model is prepared once and every backend probed once per campaign`() {
        val model = SmokeModel()
        val portfolio = fakePortfolio(VerificationVerdict.Passed)

        val records = runCampaign(model, listOf(portfolio), listOf(OptimizationLevel.All, OptimizationLevel.None))

        assertThat(records).hasSize(2)
        assertThat(model.preparations).isEqualTo(1)
        assertThat(portfolio.probes).isEqualTo(1)
    }

    @Test
    suspend fun `a cell on an unavailable backend is unavailable`() {
        val portfolio = ProbedPortfolio(
            SingleBackendPortfolio(FakeBackend.verdict("fake", VerificationVerdict.Passed), FakeConfig()),
            AvailabilityReport.Unavailable("fake: not installed"),
        )

        val record = runCampaign(SmokeModel(), listOf(portfolio)).single()

        assertThat(record.measurement.outcome).isEqualTo(Outcome.Unavailable)
        assertThat(record.measurement.message).isEqualTo("fake: not installed")
    }
}
