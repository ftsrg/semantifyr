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

class NuxmvSmokeTest {

    companion object {

        private val injector = OxstsStandaloneSetup().createInjectorAndDoEMFRegistration()
        private val helper = injector.getInstance(SemantifyrVerifierTestHelper::class.java)

        @JvmStatic
        @BeforeAll
        fun assumeNuxmvAvailable() {
            assumeBackendAvailable(NuxmvExecutorKey)
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
            verificationPortfolio = SingleBackendPortfolio(NuxmvBackend(), NuxmvConfig.Ic3Invar),
            validationPortfolio = SingleBackendPortfolio(NuxmvBackend(), NuxmvConfig.Ic3Invar),
            outputDirectory = SemantifyrVerifierTestHelper.testArtifactRoot(NuxmvSmokeTest::class.java),
        )
    }
}
