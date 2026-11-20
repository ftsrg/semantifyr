/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.nuxmv

import hu.bme.mit.semantifyr.backends.nuxmv.verification.NuxmvBackend
import hu.bme.mit.semantifyr.backends.nuxmv.verification.NuxmvConfig
import hu.bme.mit.semantifyr.oxsts.lang.OxstsStandaloneSetup
import hu.bme.mit.semantifyr.verifier.SemantifyrVerifierTestHelper
import hu.bme.mit.semantifyr.verifier.assumeBackendAvailable
import hu.bme.mit.semantifyr.verifier.portfolio.SingleBackendPortfolio
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.io.path.Path

class NuxmvWitnessReparseTest {

    companion object {

        private val injector = OxstsStandaloneSetup().createInjectorAndDoEMFRegistration()
        private val helper = injector.getInstance(SemantifyrVerifierTestHelper::class.java)

        @JvmStatic
        @BeforeAll
        fun assumeNuxmvAvailable() {
            assumeBackendAvailable(NuxmvExecutorKey)
        }
    }

    private val portfolio = SingleBackendPortfolio(NuxmvBackend(), NuxmvConfig.Ic3Invar)
    private val outputDirectory = SemantifyrVerifierTestHelper.testArtifactRoot(NuxmvWitnessReparseTest::class.java)

    @Test
    suspend fun `a witness with a half delay reparses and re-verifies on nuXmv`() {
        helper.checkWitnessReparses(
            modelPath = Path("build/test-models/semantic/clocks.semantifyr"),
            qualifiedName = "semantifyr::verification::lang::clocks::ClockStrictTestDenseReach",
            verificationPortfolio = portfolio,
            outputDirectory = outputDirectory,
        )
    }
}
