/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import hu.bme.mit.semantifyr.backend.ExecutionEnvironment
import hu.bme.mit.semantifyr.backend.VerificationCase
import hu.bme.mit.semantifyr.backends.uppaal.UppaalExecutorSpec
import hu.bme.mit.semantifyr.backends.uppaal.execution.ShellBasedUppaalExecutor
import hu.bme.mit.semantifyr.backends.uppaal.verification.UppaalBackend
import hu.bme.mit.semantifyr.backends.uppaal.verification.UppaalConfig
import hu.bme.mit.semantifyr.backends.uppaal.verification.uppaal
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
class UppaalSemanticConformanceTest {

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
            .uppaal(UppaalExecutorSpec.Auto)
            .build()

        @JvmStatic
        @BeforeAll
        fun assumeVerifytaAvailable() {
            Assumptions.assumeTrue(
                ShellBasedUppaalExecutor().isAvailable(),
                "verifyta not on PATH - skipping Uppaal conformance tests",
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
            verificationPortfolio = SingleBackendPortfolio(UppaalBackend, UppaalConfig.Default, "conformance-uppaal-default"),
            outputDirectory = SemantifyrVerifierTestHelper.testArtifactRoot(UppaalSemanticConformanceTest::class.java),
            validationPortfolio = Portfolios.AllAgree,
            environment = environment,
        )
    }
}
