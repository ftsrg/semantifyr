/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.theta

import hu.bme.mit.semantifyr.backend.VerificationCase
import hu.bme.mit.semantifyr.backends.theta.execution.ShellBasedThetaXstsExecutor
import hu.bme.mit.semantifyr.oxsts.lang.OxstsStandaloneSetup
import hu.bme.mit.semantifyr.oxsts.lang.tests.InjectWithOxsts
import hu.bme.mit.semantifyr.portfolios.Portfolios
import hu.bme.mit.semantifyr.verification.SemantifyrVerifierTestHelper
import hu.bme.mit.semantifyr.verification.SingleBackendPortfolio
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import kotlin.io.path.Path

@InjectWithOxsts
class ThetaSemanticConformanceTest {

    companion object {

        private val injector = OxstsStandaloneSetup().createInjectorAndDoEMFRegistration()
        private val helper = injector.getInstance(SemantifyrVerifierTestHelper::class.java)

        private val corpus by lazy {
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
        fun `Semantic conformance - corpus case`(): List<Arguments> {
            return helper.collectVerificationCasesAsArguments(corpus)
        }
    }

    @ParameterizedTest
    @MethodSource
    suspend fun `Semantic conformance - corpus case`(verificationCase: VerificationCase) {
        helper.checkTestModel(
            corpus,
            verificationCase,
            verificationPortfolio = SingleBackendPortfolio(
                ThetaBackend,
                ThetaConfig.CegarExplPredCombined,
                "conformance-cegar-combined",
            ),
            outputDirectory = SemantifyrVerifierTestHelper.testArtifactRoot(ThetaSemanticConformanceTest::class.java),
            validationPortfolio = Portfolios.AllAgree,
        )
    }
}
