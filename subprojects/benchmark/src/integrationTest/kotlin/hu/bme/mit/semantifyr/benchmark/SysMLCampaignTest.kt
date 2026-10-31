/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.benchmark

import hu.bme.mit.semantifyr.backend.VerificationVerdict
import hu.bme.mit.semantifyr.backend.execution.ExecutionEnvironment
import hu.bme.mit.semantifyr.backends.theta.ThetaExecutorKey
import hu.bme.mit.semantifyr.benchmark.sources.SysMLCorpusSource
import hu.bme.mit.semantifyr.benchmark.sources.SysMLMeasurement
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
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

class SysMLCampaignTest {

    companion object {
        private val injector = OxstsStandaloneSetup().createInjectorAndDoEMFRegistration()

        @JvmStatic
        @BeforeAll
        fun assumeThetaAvailable() {
            assumeBackendAvailable(ThetaExecutorKey)
        }
    }

    @Test
    suspend fun `a sysml cell records the frontend's transformation time and input counters`(@TempDir directory: Path) {
        val source = SysMLCorpusSource(injector, Path("../frontends/sysml/models/examples"))
        val campaign = campaign("sysml") {
            axis(VerificationAxes.model, listOf(source.model("door_access.sysml")))
            axis(VerificationAxes.case) {
                it[VerificationAxes.model].cases.filter { case -> case.name == "LockedReachable" }
            }
            axis(VerificationAxes.backendConfig, listOf(Portfolios.ThetaCegarExplPredCombined))
            axis(VerificationAxes.optimization, listOf(OptimizationLevel.All))
            repetitions = 1
            warmups = 0
            timeout = 2.minutes
        }
        val action = VerificationCellAction(injector, campaign.timeout, ExecutionEnvironment.builder().build(), directory.resolve("models"))

        val measurement = CampaignRunner(campaign, ResultStore(directory), action).run().single().measurement

        assertThat(measurement.verdict).isEqualTo(VerificationVerdict.Passed)
        assertThat(measurement.asExpected).isTrue()
        val sysml = SysMLMeasurement.extension.read(measurement)!!
        assertThat(sysml.verificationCases).isEqualTo(9)
        assertThat(sysml.transformationDuration).isGreaterThan(Duration.ZERO)
    }
}
