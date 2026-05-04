/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.theta

import hu.bme.mit.semantifyr.backends.theta.execution.ShellBasedThetaXstsExecutor
import hu.bme.mit.semantifyr.oxsts.lang.OxstsStandaloneSetup
import hu.bme.mit.semantifyr.oxsts.lang.tests.InjectWithOxsts
import hu.bme.mit.semantifyr.verification.SemantifyrVerifierTestHelper
import hu.bme.mit.semantifyr.verification.VerificationCase
import hu.bme.mit.semantifyr.verification.portfolio.SingleBackendPortfolio
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import kotlin.io.path.Path

@InjectWithOxsts
class ThetaVerificationTests {

    companion object {

        private val injector = OxstsStandaloneSetup().createInjectorAndDoEMFRegistration()
        private val helper = injector.getInstance(SemantifyrVerifierTestHelper::class.java)

        private val context by lazy {
            helper.semantifyrLoader
                .startContext()
                .loadModels(Path("build/test-models/simple"))
                .buildAndResolve()
        }

        @JvmStatic
        @BeforeAll
        fun assumeThetaAvailable() {
            Assumptions.assumeTrue(
                ShellBasedThetaXstsExecutor().isAvailable(),
                "theta-xsts-cli not on PATH - skipping Theta backend tests",
            )
        }

        @JvmStatic
        fun `OXSTS Simple Test Suite Passes`(): List<Arguments> {
            return helper.collectVerificationCasesAsArguments(context)
        }
    }

    @ParameterizedTest
    @MethodSource
    suspend fun `OXSTS Simple Test Suite Passes`(verificationCase: VerificationCase) {
        helper.checkTestModel(
            context,
            verificationCase,
            verificationPortfolio = SingleBackendPortfolio(ThetaBackend(), ThetaConfig.CegarExplPredCombined),
            outputDirectory = SemantifyrVerifierTestHelper.testArtifactRoot(ThetaVerificationTests::class.java),
        )
    }
}
