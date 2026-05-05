/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import hu.bme.mit.semantifyr.backends.uppaal.execution.ShellBasedUppaalExecutor
import hu.bme.mit.semantifyr.backends.uppaal.verification.UppaalBackend
import hu.bme.mit.semantifyr.backends.uppaal.verification.UppaalConfig
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

class UppaalSemanticConformanceTest {

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
        fun assumeVerifytaAvailable() {
            Assumptions.assumeTrue(
                ShellBasedUppaalExecutor().isAvailable(),
                "verifyta not on PATH - skipping Uppaal conformance tests",
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
            verificationPortfolio = SingleBackendPortfolio(UppaalBackend(), UppaalConfig.Default),
            outputDirectory = SemantifyrVerifierTestHelper.testArtifactRoot(UppaalSemanticConformanceTest::class.java),
            validationPortfolio = Portfolios.AllAgree,
        )
    }
}
