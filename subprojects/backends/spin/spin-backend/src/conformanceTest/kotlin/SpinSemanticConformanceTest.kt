/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import hu.bme.mit.semantifyr.backend.ExecutionEnvironment
import hu.bme.mit.semantifyr.backend.VerificationCase
import hu.bme.mit.semantifyr.backends.spin.SpinExecutorSpec
import hu.bme.mit.semantifyr.backends.spin.execution.ShellBasedSpinExecutor
import hu.bme.mit.semantifyr.backends.spin.verification.SpinBackend
import hu.bme.mit.semantifyr.backends.spin.verification.SpinConfig
import hu.bme.mit.semantifyr.backends.spin.verification.spin
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
class SpinSemanticConformanceTest {

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
            .spin(SpinExecutorSpec.Auto)
            .build()

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
            return helper.collectVerificationCasesAsArguments(corpus)
        }
    }

    @ParameterizedTest
    @MethodSource("corpusCases")
    suspend fun `Semantic conformance - corpus case`(verificationCase: VerificationCase) {
        helper.checkTestModel(
            corpus,
            verificationCase,
            verificationPortfolio = SingleBackendPortfolio(SpinBackend, SpinConfig.SafeDfs, "conformance-spin-safe-dfs"),
            outputDirectory = SemantifyrVerifierTestHelper.testArtifactRoot(SpinSemanticConformanceTest::class.java),
            validationPortfolio = Portfolios.AllAgree,
            environment = environment,
        )
    }
}
