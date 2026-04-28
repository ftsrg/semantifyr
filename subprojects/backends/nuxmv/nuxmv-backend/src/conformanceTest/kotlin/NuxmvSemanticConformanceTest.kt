/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import hu.bme.mit.semantifyr.backend.ExecutionEnvironment
import hu.bme.mit.semantifyr.backend.VerificationCase
import hu.bme.mit.semantifyr.backends.nuxmv.NuxmvExecutorSpec
import hu.bme.mit.semantifyr.backends.nuxmv.execution.ShellBasedNuxmvExecutor
import hu.bme.mit.semantifyr.backends.nuxmv.verification.NuxmvBackend
import hu.bme.mit.semantifyr.backends.nuxmv.verification.NuxmvConfig
import hu.bme.mit.semantifyr.backends.nuxmv.verification.nuxmv
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
class NuxmvSemanticConformanceTest {

    companion object {

        private val injector = OxstsStandaloneSetup().createInjectorAndDoEMFRegistration()
        private val helper = injector.getInstance(SemantifyrVerifierTestHelper::class.java)

        private val corpus by lazy {
            helper.semantifyrLoader
                .startContext()
                .loadModels(Path("build/test-models/semantic"))
                .buildAndResolve()
        }

        private val environment = ExecutionEnvironment
            .builder()
            .nuxmv(NuxmvExecutorSpec.Auto)
            .build()

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
            return helper.collectVerificationCasesAsArguments(corpus)
        }
    }

    @ParameterizedTest
    @MethodSource
    suspend fun `OXSTS Semantic Test Suite Passes`(verificationCase: VerificationCase) {
        helper.checkTestModel(
            corpus,
            verificationCase,
            verificationPortfolio = SingleBackendPortfolio(NuxmvBackend, NuxmvConfig.Ic3Invar, "conformance-nuxmv-ic3"),
            outputDirectory = SemantifyrVerifierTestHelper.testArtifactRoot(NuxmvSemanticConformanceTest::class.java),
            validationPortfolio = Portfolios.AllAgree,
            environment = environment,
        )
    }
}
