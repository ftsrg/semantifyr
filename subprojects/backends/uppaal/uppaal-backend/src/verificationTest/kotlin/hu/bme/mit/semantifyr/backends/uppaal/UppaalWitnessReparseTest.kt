/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.uppaal

import hu.bme.mit.semantifyr.backends.theta.ThetaBackend
import hu.bme.mit.semantifyr.backends.theta.ThetaConfig
import hu.bme.mit.semantifyr.backends.theta.ThetaExecutorKey
import hu.bme.mit.semantifyr.backends.uppaal.verification.UppaalBackend
import hu.bme.mit.semantifyr.backends.uppaal.verification.UppaalConfig
import hu.bme.mit.semantifyr.oxsts.lang.OxstsStandaloneSetup
import hu.bme.mit.semantifyr.verifier.SemantifyrVerifierTestHelper
import hu.bme.mit.semantifyr.verifier.assumeBackendAvailable
import hu.bme.mit.semantifyr.verifier.portfolio.SingleBackendPortfolio
import hu.bme.mit.semantifyr.verifier.portfolio.VerificationPortfolio
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.io.path.Path

class UppaalWitnessReparseTest {

    companion object {

        private val injector = OxstsStandaloneSetup().createInjectorAndDoEMFRegistration()
        private val helper = injector.getInstance(SemantifyrVerifierTestHelper::class.java)

        @JvmStatic
        @BeforeAll
        fun assumeBackendsAvailable() {
            assumeBackendAvailable(UppaalExecutorKey)
            assumeBackendAvailable(ThetaExecutorKey)
        }
    }

    private val portfolio = SingleBackendPortfolio(UppaalBackend(), UppaalConfig.Default)
    private val outputDirectory = SemantifyrVerifierTestHelper.testArtifactRoot(UppaalWitnessReparseTest::class.java)

    @Test
    suspend fun `a witness with a half delay reparses scaled by 2 and re-verifies on Uppaal`() {
        checkStrictTestWitness(portfolio)
    }

    @Test
    suspend fun `a witness with a half delay reparses scaled by 2 and re-verifies on Theta`() {
        checkStrictTestWitness(SingleBackendPortfolio(ThetaBackend(), ThetaConfig.CegarExplPredCombined))
    }

    private suspend fun checkStrictTestWitness(validationPortfolio: VerificationPortfolio) {
        val checked = helper.checkWitnessReparses(
            modelPath = Path("build/test-models/semantic/clocks.semantifyr"),
            qualifiedName = "semantifyr::verification::lang::clocks::ClockStrictTestDenseReach",
            verificationPortfolio = portfolio,
            outputDirectory = outputDirectory,
            validationPortfolio = validationPortfolio,
        )

        assertThat(requireNotNull(checked.report.result.witness).timeScale).isEqualTo(2)
    }
}
