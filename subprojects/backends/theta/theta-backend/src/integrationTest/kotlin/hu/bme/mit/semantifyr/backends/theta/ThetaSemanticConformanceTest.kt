/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.theta

import hu.bme.mit.semantifyr.backends.theta.execution.ShellBasedThetaXstsExecutor
import hu.bme.mit.semantifyr.oxsts.lang.OxstsStandaloneSetup
import hu.bme.mit.semantifyr.portfolios.Portfolios
import hu.bme.mit.semantifyr.verifier.SemantifyrVerifierTestHelper
import hu.bme.mit.semantifyr.verifier.VerificationCase
import hu.bme.mit.semantifyr.verifier.portfolio.SingleBackendPortfolio
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import kotlin.io.path.Path

class ThetaSemanticConformanceTest {

    companion object {

        private val injector = OxstsStandaloneSetup().createInjectorAndDoEMFRegistration()
        private val helper = injector.getInstance(SemantifyrVerifierTestHelper::class.java)

        private val context by lazy {
            helper.semantifyrLoader.startContext()
                .loadModels(Path("build/test-models/semantic"))
                .buildAndResolve()
        }

        @JvmStatic
        @BeforeAll
        fun assumeThetaAvailable() {
            Assumptions.assumeTrue(
                ShellBasedThetaXstsExecutor().isAvailable(),
                "theta-xsts-cli not on PATH - skipping Theta conformance tests",
            )
        }

        @JvmStatic
        fun `OXSTS Semantic Test Suite Passes`(): List<Arguments> {
            return helper.collectVerificationCasesAsArguments(context)
        }
    }

    @ParameterizedTest
    @MethodSource
    suspend fun `OXSTS Semantic Test Suite Passes`(verificationCase: VerificationCase) {
        helper.checkTestModel(
            context,
            verificationCase,
            verificationPortfolio = SingleBackendPortfolio(ThetaBackend(), ThetaConfig.CegarExplPredCombined),
            outputDirectory = SemantifyrVerifierTestHelper.testArtifactRoot(ThetaSemanticConformanceTest::class.java),
            validationPortfolio = Portfolios.AllAgree,
        )
    }
}
