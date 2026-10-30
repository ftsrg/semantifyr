/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.spin

import hu.bme.mit.semantifyr.backend.VerificationVerdict
import hu.bme.mit.semantifyr.backends.spin.verification.SpinBackend
import hu.bme.mit.semantifyr.backends.spin.verification.SpinConfig
import hu.bme.mit.semantifyr.oxsts.lang.OxstsStandaloneSetup
import hu.bme.mit.semantifyr.oxsts.lang.tests.InjectWithOxsts
import hu.bme.mit.semantifyr.verifier.SemantifyrVerifierTestHelper
import hu.bme.mit.semantifyr.verifier.assumeBackendAvailable
import hu.bme.mit.semantifyr.verifier.portfolio.SingleBackendPortfolio
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlin.io.path.Path
import kotlin.time.Duration.Companion.minutes

@Tag("slow")
@InjectWithOxsts
class SpinSearchDepthTests {

    companion object {

        private val injector = OxstsStandaloneSetup().createInjectorAndDoEMFRegistration()
        private val helper = injector.getInstance(SemantifyrVerifierTestHelper::class.java)

        private val context by lazy {
            helper.semantifyrLoader
                .startContext()
                .loadModels(Path("build/test-models/deep-reachability"))
                .buildAndResolve()
        }

        @JvmStatic
        @BeforeAll
        fun assumeSpinAvailable() {
            assumeBackendAvailable(SpinExecutorKey)
        }
    }

    @Test
    suspend fun `Deep Target Is Inconclusive At Pan Default Depth`() {
        checkDeepCounterReachable(
            config = SpinConfig.SafeDfs,
            outputName = "default-depth",
            expectedVerdict = VerificationVerdict.Inconclusive,
        )
    }

    @Test
    suspend fun `Deep Target Passes With Raised Search Depth`() {
        checkDeepCounterReachable(
            config = SpinConfig.SafeDfs.copy(maxSearchDepth = 1_000_000),
            outputName = "raised-depth",
            expectedVerdict = VerificationVerdict.Passed,
        )
    }

    private suspend fun checkDeepCounterReachable(
        config: SpinConfig,
        outputName: String,
        expectedVerdict: VerificationVerdict,
    ) {
        val case = helper.collectVerificationCases(context).single {
            it.qualifiedName.endsWith("::DeepCounterReachable")
        }
        helper.checkVerificationCase(
            context = context,
            case = case,
            verificationPortfolio = SingleBackendPortfolio(SpinBackend(), config),
            outputDirectory = SemantifyrVerifierTestHelper.testArtifactRoot(SpinSearchDepthTests::class.java).resolve(outputName),
            timeout = 2.minutes,
            expectedVerdict = expectedVerdict,
        )
    }
}
