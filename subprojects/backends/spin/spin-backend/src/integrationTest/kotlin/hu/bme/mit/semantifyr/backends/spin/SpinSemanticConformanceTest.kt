/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.spin

import hu.bme.mit.semantifyr.backends.spin.execution.ShellBasedSpinExecutor
import hu.bme.mit.semantifyr.backends.spin.verification.SpinBackend
import hu.bme.mit.semantifyr.backends.spin.verification.SpinConfig
import hu.bme.mit.semantifyr.oxsts.lang.OxstsStandaloneSetup
import hu.bme.mit.semantifyr.oxsts.lang.tests.InjectWithOxsts
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

@InjectWithOxsts
class SpinSemanticConformanceTest {

    companion object {

        private val injector = OxstsStandaloneSetup().createInjectorAndDoEMFRegistration()
        private val helper = injector.getInstance(SemantifyrVerifierTestHelper::class.java)

        private val context by lazy {
            helper.semantifyrLoader
                .startContext()
                .loadModels(Path("build/test-models/semantic"))
                .buildAndResolve()
        }

        @JvmStatic
        @BeforeAll
        fun assumeSpinAvailable() {
            Assumptions.assumeTrue(
                ShellBasedSpinExecutor().isAvailable(),
                "spin not on PATH - skipping Spin conformance tests",
            )
        }

        @JvmStatic
        fun corpusCases(): List<Arguments> {
            return helper.collectVerificationCasesAsArguments(context)
        }
    }

    @ParameterizedTest
    @MethodSource("corpusCases")
    suspend fun `Semantic conformance - corpus case`(verificationCase: VerificationCase) {
        helper.checkTestModel(
            context,
            verificationCase,
            verificationPortfolio = SingleBackendPortfolio(SpinBackend(), SpinConfig.Companion.SafeDfs),
            outputDirectory = SemantifyrVerifierTestHelper.Companion.testArtifactRoot(SpinSemanticConformanceTest::class.java),
            validationPortfolio = Portfolios.AllAgree,
        )
    }
}
