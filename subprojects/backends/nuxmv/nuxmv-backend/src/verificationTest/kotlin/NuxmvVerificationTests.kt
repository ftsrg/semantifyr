/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import hu.bme.mit.semantifyr.backends.nuxmv.execution.ShellBasedNuxmvExecutor
import hu.bme.mit.semantifyr.backends.nuxmv.verification.NuxmvBackend
import hu.bme.mit.semantifyr.backends.nuxmv.verification.NuxmvConfig
import hu.bme.mit.semantifyr.oxsts.lang.OxstsStandaloneSetup
import hu.bme.mit.semantifyr.verifier.SemantifyrVerifierTestHelper
import hu.bme.mit.semantifyr.verifier.VerificationCase
import hu.bme.mit.semantifyr.verifier.portfolio.SingleBackendPortfolio
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import kotlin.io.path.Path

class NuxmvVerificationTests {

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
        fun assumeNuxmvAvailable() {
            Assumptions.assumeTrue(
                ShellBasedNuxmvExecutor().isAvailable(),
                "nuXmv not on PATH - skipping nuXmv backend tests",
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
            verificationPortfolio = SingleBackendPortfolio(NuxmvBackend(), NuxmvConfig.Ic3Invar),
            outputDirectory = SemantifyrVerifierTestHelper.testArtifactRoot(NuxmvVerificationTests::class.java),
        )
    }
}
