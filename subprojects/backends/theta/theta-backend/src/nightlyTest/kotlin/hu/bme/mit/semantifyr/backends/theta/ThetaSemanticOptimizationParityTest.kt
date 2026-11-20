/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.theta

import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationConfig
import hu.bme.mit.semantifyr.oxsts.lang.OxstsStandaloneSetup
import hu.bme.mit.semantifyr.verifier.SemantifyrVerifierTestHelper
import hu.bme.mit.semantifyr.verifier.VerificationCase
import hu.bme.mit.semantifyr.verifier.assertVerdict
import hu.bme.mit.semantifyr.verifier.assumeBackendAvailable
import hu.bme.mit.semantifyr.verifier.portfolio.SingleBackendPortfolio
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
        fun `Without Optimization Every Case Has Its Expected Verdict`(): List<Arguments> {
            return helper.collectAcceptedCasesAsArguments(
                context,
                ThetaBackend(),
                ThetaConfig.CegarExplPredCombined,
                outputDirectory.resolve("capability-check"),
            )
        }
    }

    @ParameterizedTest
    @MethodSource
    suspend fun `Without Optimization Every Case Has Its Expected Verdict`(verificationCase: VerificationCase) {
        val outcome = helper.runVerificationCase(
            context = context,
            case = verificationCase,
            verificationPortfolio = portfolio,
            outputDirectory = outputDirectory,
            timeout = 1.minutes,
            optimization = OptimizationConfig.NONE,
        )
        assertVerdict(verificationCase.qualifiedName, helper.expectedVerdictFromTags(verificationCase), outcome.report.result)
    }
}
