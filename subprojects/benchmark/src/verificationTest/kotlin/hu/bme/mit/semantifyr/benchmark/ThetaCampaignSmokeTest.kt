/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.benchmark

import hu.bme.mit.semantifyr.backend.VerificationVerdict
import hu.bme.mit.semantifyr.backend.execution.ExecutionEnvironment
import hu.bme.mit.semantifyr.backends.theta.ThetaExecutorKey
import hu.bme.mit.semantifyr.benchmark.sources.OxstsModelSource
import hu.bme.mit.semantifyr.benchmark.verification.OptimizationLevel
import hu.bme.mit.semantifyr.benchmark.verification.VerificationAxes
import hu.bme.mit.semantifyr.benchmark.verification.VerificationCellAction
import hu.bme.mit.semantifyr.oxsts.lang.OxstsStandaloneSetup
import hu.bme.mit.semantifyr.portfolios.Portfolios
import hu.bme.mit.semantifyr.verifier.assumeBackendAvailable
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

class ThetaCampaignSmokeTest {

    companion object {
        private val injector = OxstsStandaloneSetup().createInjectorAndDoEMFRegistration()

        @JvmStatic
        @BeforeAll
        fun assumeThetaAvailable() {
            assumeBackendAvailable(ThetaExecutorKey)
        }
    }

    @Test
    suspend fun `a campaign over the smoke model measures a real theta run`(@TempDir directory: Path) {
        val campaign = campaign("smoke") {
            axis(VerificationAxes.model, OxstsModelSource(injector, Path("../../oxsts-test-models/smoke")).models)
            axis(VerificationAxes.case) {
                it[VerificationAxes.model].cases
            }
            axis(VerificationAxes.backendConfig, listOf(Portfolios.ThetaCegarExplPredCombined))
            axis(VerificationAxes.optimization, listOf(OptimizationLevel.All))
            repetitions = 1
            timeout = 2.minutes
        }
        val action = VerificationCellAction(injector, campaign.timeout, ExecutionEnvironment.builder().build(), directory.resolve("models"))

        val records = CampaignRunner(campaign, ResultStore(directory), action).run()

        assertThat(records.map { it.warmup }).containsExactly(true, false)
        val measurement = records.last().measurement
        assertThat(measurement.verdict).isEqualTo(VerificationVerdict.Passed)
        assertThat(measurement.asExpected).isTrue()
        assertThat(measurement.decidedBy).isEqualTo("theta:cegar-expl-pred-combined")
        assertThat(measurement.backendCpuTime!!).isGreaterThan(Duration.ZERO)
        assertThat(measurement.metrics!!.backend!!.verificationDuration).isGreaterThan(Duration.ZERO)
        assertThat(directory.resolve("cells").listDirectoryEntries()).isEmpty()
    }
}
