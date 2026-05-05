/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.nuxmv

import hu.bme.mit.semantifyr.backends.nuxmv.execution.ShellBasedNuxmvExecutor
import hu.bme.mit.semantifyr.backends.nuxmv.verification.NuxmvBackend
import hu.bme.mit.semantifyr.backends.nuxmv.verification.NuxmvConfig
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

class NuxmvSemanticConformanceTest {

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
        fun assumeNuxmvAvailable() {
            Assumptions.assumeTrue(
                ShellBasedNuxmvExecutor().isAvailable(),
                "nuXmv not on PATH - skipping nuXmv conformance tests",
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
            verificationPortfolio = SingleBackendPortfolio(NuxmvBackend(), NuxmvConfig.Companion.Ic3Invar),
            outputDirectory = SemantifyrVerifierTestHelper.Companion.testArtifactRoot(NuxmvSemanticConformanceTest::class.java),
            validationPortfolio = Portfolios.AllAgree,
        )
    }
}
