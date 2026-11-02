/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.theta

import hu.bme.mit.semantifyr.backend.VerificationVerdict
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationConfig
import hu.bme.mit.semantifyr.oxsts.lang.OxstsStandaloneSetup
import hu.bme.mit.semantifyr.verifier.SemantifyrVerifierTestHelper
import hu.bme.mit.semantifyr.verifier.VerificationCase
import hu.bme.mit.semantifyr.verifier.VerificationResult
import hu.bme.mit.semantifyr.verifier.assumeBackendAvailable
import hu.bme.mit.semantifyr.verifier.portfolio.SingleBackendPortfolio
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import kotlin.io.path.Path
import kotlin.time.Duration.Companion.minutes

class ThetaSemanticOptimizationParityTest {

    companion object {

        private val injector = OxstsStandaloneSetup().createInjectorAndDoEMFRegistration()
        private val helper = injector.getInstance(SemantifyrVerifierTestHelper::class.java)
        private val portfolio = SingleBackendPortfolio(ThetaBackend(), ThetaConfig.CegarExplPredCombined)
        private val outputDirectory = SemantifyrVerifierTestHelper.testArtifactRoot(ThetaSemanticOptimizationParityTest::class.java)

        private val context by lazy {
            helper.semantifyrLoader
                .startContext()
                .loadModels(Path("build/test-models/semantic"))
                .buildAndResolve()
        }

        @JvmStatic
        @BeforeAll
        fun assumeThetaAvailable() {
            assumeBackendAvailable(ThetaExecutorKey)
        }

        @JvmStatic
        fun `Every Optimization Keeps The Verdict Of No Optimization`(): List<Arguments> {
            return helper.collectVerificationCasesAsArguments(context)
        }
    }

    @ParameterizedTest
    @MethodSource
    suspend fun `Every Optimization Keeps The Verdict Of No Optimization`(verificationCase: VerificationCase) {
        val unoptimized = verify(verificationCase, OptimizationConfig.NONE, "none")
        val optimized = verify(verificationCase, OptimizationConfig.ALL, "all")

        val verdicts = "${verificationCase.qualifiedName}: ${describe(unoptimized)} at optimization none, ${describe(optimized)} at optimization all"
        Assertions.assertNotEquals(VerificationVerdict.Errored, unoptimized.verdict, verdicts)
        Assertions.assertNotEquals(VerificationVerdict.Errored, optimized.verdict, verdicts)
        Assertions.assertNotEquals(VerificationVerdict.Timeout, unoptimized.verdict, verdicts)
        Assertions.assertNotEquals(VerificationVerdict.Timeout, optimized.verdict, verdicts)
        Assertions.assertEquals(unoptimized.verdict, optimized.verdict, verdicts)
    }

    private suspend fun verify(
        verificationCase: VerificationCase,
        optimization: OptimizationConfig,
        name: String,
    ): VerificationResult {
        return helper.runVerificationCase(
            context = context,
            case = verificationCase,
            verificationPortfolio = portfolio,
            outputDirectory = outputDirectory.resolve(name),
            timeout = 1.minutes,
            optimization = optimization,
        )
    }

    private fun describe(result: VerificationResult): String {
        return "${result.verdict} (${result.message ?: "no message"})"
    }
}
