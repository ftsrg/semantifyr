/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.uppaal

import hu.bme.mit.semantifyr.backends.uppaal.verification.UppaalBackend
import hu.bme.mit.semantifyr.backends.uppaal.verification.UppaalConfig
import hu.bme.mit.semantifyr.oxsts.lang.OxstsStandaloneSetup
import hu.bme.mit.semantifyr.verifier.SemantifyrVerifierTestHelper
import hu.bme.mit.semantifyr.verifier.assumeBackendAvailable
import hu.bme.mit.semantifyr.verifier.portfolio.SingleBackendPortfolio
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.io.path.Path

class UppaalWitnessReparseTest {

    companion object {

        private val injector = OxstsStandaloneSetup().createInjectorAndDoEMFRegistration()
        private val helper = injector.getInstance(SemantifyrVerifierTestHelper::class.java)

        @JvmStatic
        @BeforeAll
        fun assumeUppaalAvailable() {
            assumeBackendAvailable(UppaalExecutorKey)
        }
    }

    private val portfolio = SingleBackendPortfolio(UppaalBackend(), UppaalConfig.Default)
    private val outputDirectory = SemantifyrVerifierTestHelper.testArtifactRoot(UppaalWitnessReparseTest::class.java)

    @Test
    suspend fun `a witness with a half delay reparses and re-verifies on Uppaal`() {
        helper.checkWitnessReparses(
            modelPath = Path("build/test-models/semantic/clocks.semantifyr"),
            qualifiedName = "semantifyr::verification::lang::clocks::ClockStrictTestDenseReach",
            verificationPortfolio = portfolio,
            outputDirectory = outputDirectory,
        )
    }
}
