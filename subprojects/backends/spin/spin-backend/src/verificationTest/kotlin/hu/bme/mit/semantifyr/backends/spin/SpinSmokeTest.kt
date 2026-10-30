/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.spin

import hu.bme.mit.semantifyr.backends.spin.verification.SpinBackend
import hu.bme.mit.semantifyr.backends.spin.verification.SpinConfig
import hu.bme.mit.semantifyr.oxsts.lang.OxstsStandaloneSetup
import hu.bme.mit.semantifyr.verifier.SemantifyrVerifierTestHelper
import hu.bme.mit.semantifyr.verifier.assumeBackendAvailable
import hu.bme.mit.semantifyr.verifier.portfolio.SingleBackendPortfolio
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.io.path.Path

class SpinSmokeTest {

    companion object {

        private val injector = OxstsStandaloneSetup().createInjectorAndDoEMFRegistration()
        private val helper = injector.getInstance(SemantifyrVerifierTestHelper::class.java)

        @JvmStatic
        @BeforeAll
        fun assumeSpinAvailable() {
            assumeBackendAvailable(SpinExecutorKey)
        }
    }

    @Test
    suspend fun `smoke case verifies and its witness re-verifies`() {
        val context = helper.semantifyrLoader
            .startContext()
            .loadModels(Path("build/test-models/smoke"))
            .buildAndResolve()

        helper.checkVerificationCase(
            context = context,
            case = helper.collectVerificationCases(context).single(),
            verificationPortfolio = SingleBackendPortfolio(SpinBackend(), SpinConfig.SafeDfs),
            validationPortfolio = SingleBackendPortfolio(SpinBackend(), SpinConfig.SafeDfs),
            outputDirectory = SemantifyrVerifierTestHelper.testArtifactRoot(SpinSmokeTest::class.java),
        )
    }
}
