/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.spin

import hu.bme.mit.semantifyr.backends.spin.transformation.SpinCapabilityChecker
import hu.bme.mit.semantifyr.backends.spin.verification.SpinBackend
import hu.bme.mit.semantifyr.backends.spin.verification.SpinBackendModule
import hu.bme.mit.semantifyr.backends.spin.verification.SpinConfig
import hu.bme.mit.semantifyr.oxsts.lang.OxstsStandaloneSetup
import hu.bme.mit.semantifyr.oxsts.lang.tests.InjectWithOxsts
import hu.bme.mit.semantifyr.verifier.EngineVerdictPortfolio
import hu.bme.mit.semantifyr.verifier.SemantifyrVerifierTestHelper
import hu.bme.mit.semantifyr.verifier.VerificationCase
import hu.bme.mit.semantifyr.verifier.assumeBackendAvailable
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
            assumeBackendAvailable(SpinExecutorKey)
        }

        @JvmStatic
        fun corpusCases(): List<Arguments> {
            return helper.collectAcceptedCasesAsArguments(
                context,
                SpinBackendModule(),
                SpinCapabilityChecker::class.java,
                SemantifyrVerifierTestHelper.testArtifactRoot(SpinSemanticConformanceTest::class.java).resolve("capability-check"),
            )
        }
    }

    @ParameterizedTest
    @MethodSource("corpusCases")
    suspend fun `Semantic conformance - corpus case`(verificationCase: VerificationCase) {
        helper.checkTestModel(
            context,
            verificationCase,
            verificationPortfolio = EngineVerdictPortfolio(SpinBackend(), SpinConfig.SafeDfs),
            outputDirectory = SemantifyrVerifierTestHelper.testArtifactRoot(SpinSemanticConformanceTest::class.java),
        )
    }
}
