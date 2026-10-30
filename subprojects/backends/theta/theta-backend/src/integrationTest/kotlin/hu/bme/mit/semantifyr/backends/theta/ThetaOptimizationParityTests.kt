/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.theta

import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationConfig
import hu.bme.mit.semantifyr.oxsts.lang.OxstsStandaloneSetup
import hu.bme.mit.semantifyr.oxsts.lang.tests.InjectWithOxsts
import hu.bme.mit.semantifyr.verifier.SemantifyrVerifierTestHelper
import hu.bme.mit.semantifyr.verifier.assumeBackendAvailable
import hu.bme.mit.semantifyr.verifier.portfolio.SingleBackendPortfolio
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.io.path.Path
import kotlin.time.Duration.Companion.minutes

@InjectWithOxsts
class ThetaOptimizationParityTests {

    companion object {

        private val injector = OxstsStandaloneSetup().createInjectorAndDoEMFRegistration()
        private val helper = injector.getInstance(SemantifyrVerifierTestHelper::class.java)

        private val context by lazy {
            helper.semantifyrLoader
                .startContext()
                .loadModels(Path("build/test-models/constant-guards"))
                .buildAndResolve()
        }

        @JvmStatic
        @BeforeAll
        fun assumeThetaAvailable() {
            assumeBackendAvailable(ThetaExecutorKey)
        }
    }

    @Test
    suspend fun `Constant Guard Model Passes Without Optimization`() {
        checkConstantGuardReachable(OptimizationConfig.NONE, "none")
    }

    @Test
    suspend fun `Constant Guard Model Passes With Full Optimization`() {
        checkConstantGuardReachable(OptimizationConfig.ALL, "all")
    }

    private suspend fun checkConstantGuardReachable(optimization: OptimizationConfig, outputName: String) {
        val case = helper.collectVerificationCases(context).single {
            it.qualifiedName.endsWith("::ConstantGuardReachable")
        }
        helper.checkVerificationCase(
            context = context,
            case = case,
            verificationPortfolio = SingleBackendPortfolio(ThetaBackend(), ThetaConfig.CegarExpl),
            outputDirectory = SemantifyrVerifierTestHelper.testArtifactRoot(ThetaOptimizationParityTests::class.java).resolve(outputName),
            timeout = 2.minutes,
            optimization = optimization,
        )
    }
}
