/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.uppaal

import hu.bme.mit.semantifyr.backends.uppaal.verification.UppaalBackend
import hu.bme.mit.semantifyr.backends.uppaal.verification.UppaalConfig
import hu.bme.mit.semantifyr.oxsts.lang.OxstsStandaloneSetup
import hu.bme.mit.semantifyr.verifier.EngineVerdictPortfolio
import hu.bme.mit.semantifyr.verifier.SemantifyrVerifierTestHelper
import hu.bme.mit.semantifyr.verifier.VerificationCase
import hu.bme.mit.semantifyr.verifier.assumeBackendAvailable
import hu.bme.mit.semantifyr.verifier.discovery.CaseFilter
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
            assumeBackendAvailable(UppaalExecutorKey)
        }

        @JvmStatic
        fun `OXSTS Semantic Test Suite Passes`(): List<Arguments> {
            return helper.collectAcceptedCasesAsArguments(
                context,
                UppaalBackend(),
                UppaalConfig.Default,
                SemantifyrVerifierTestHelper.testArtifactRoot(UppaalSemanticConformanceTest::class.java).resolve("capability-check"),
                CaseFilter.Matching {
                    it.qualifiedName !in uppaalUndecidedCases
                },
            )
        }
    }

    @ParameterizedTest
    @MethodSource
    suspend fun `OXSTS Semantic Test Suite Passes`(verificationCase: VerificationCase) {
        helper.checkTestModel(
            context,
            verificationCase,
            verificationPortfolio = EngineVerdictPortfolio(UppaalBackend(), UppaalConfig.Default),
            outputDirectory = SemantifyrVerifierTestHelper.testArtifactRoot(UppaalSemanticConformanceTest::class.java),
        )
    }
}
