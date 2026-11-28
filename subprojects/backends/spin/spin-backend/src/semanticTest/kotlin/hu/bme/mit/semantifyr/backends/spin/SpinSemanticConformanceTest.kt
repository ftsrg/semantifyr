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
import hu.bme.mit.semantifyr.verifier.BackendVerdictPortfolio
import hu.bme.mit.semantifyr.verifier.SemantifyrVerifierTestHelper
import hu.bme.mit.semantifyr.verifier.VerificationCase
import hu.bme.mit.semantifyr.verifier.assumeBackendAvailable
import hu.bme.mit.semantifyr.verifier.assumePortfolioAvailable
import hu.bme.mit.semantifyr.verifier.discovery.CaseFilter
import hu.bme.mit.semantifyr.verifier.portfolio.SingleBackendPortfolio
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import kotlin.io.path.Path
import kotlin.time.Duration.Companion.minutes

class SpinSemanticConformanceTest {

    companion object {

        private val injector = OxstsStandaloneSetup().createInjectorAndDoEMFRegistration()
        private val helper = injector.getInstance(SemantifyrVerifierTestHelper::class.java)

        private val context by lazy {
            helper.semantifyrLoader
                .startContext()
                .loadModels(Path("../../../../oxsts-test-models/semantic"))
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
                SpinBackend(),
                SpinConfig.SafeDfs,
                SemantifyrVerifierTestHelper.testArtifactRoot(SpinSemanticConformanceTest::class.java).resolve("capability-check"),
            )
        }

        @JvmStatic
        fun `a whole-delay proof of a model whose clock tests are not all closed is Inconclusive`(): List<Arguments> {
            val proofs = CaseFilter.Matching {
                !helper.casesExpectingWitness.matches(it)
            }
            return approximatedCases(proofs)
        }

        private val approximatedTraceCases by lazy {
            approximatedCases(helper.casesExpectingWitness)
        }

        private val tracesFound = mutableListOf<Boolean>()

        @JvmStatic
        fun `a whole-delay trace of a model whose clock tests are not all closed is the tagged verdict or Inconclusive`(): List<Arguments> {
            return approximatedTraceCases
        }

        @JvmStatic
        @AfterAll
        fun `the whole-delay lowering finds a trace of some model whose clock tests are not all closed`() {
            if (tracesFound.isEmpty() || tracesFound.size < approximatedTraceCases.size) {
                return
            }
            assertThat(tracesFound).contains(true)
        }

        private fun approximatedCases(filter: CaseFilter): List<Arguments> {
            return helper.collectApproximatedCasesAsArguments(
                context,
                SpinBackend(),
                SpinConfig.SafeDfs,
                SemantifyrVerifierTestHelper.testArtifactRoot(SpinSemanticConformanceTest::class.java).resolve("lowering-check"),
                filter,
            )
        }
    }

    @ParameterizedTest
    @MethodSource("corpusCases")
    suspend fun `Semantic conformance - corpus case`(verificationCase: VerificationCase) {
        helper.checkTestModel(
            context,
            verificationCase,
            verificationPortfolio = BackendVerdictPortfolio(SpinBackend(), SpinConfig.SafeDfs),
            outputDirectory = SemantifyrVerifierTestHelper.testArtifactRoot(SpinSemanticConformanceTest::class.java),
        )
    }

    @ParameterizedTest
    @MethodSource
    suspend fun `a whole-delay proof of a model whose clock tests are not all closed is Inconclusive`(verificationCase: VerificationCase) {
        helper.checkTestModel(
            context,
            verificationCase,
            verificationPortfolio = SingleBackendPortfolio(SpinBackend(), SpinConfig.SafeDfs),
            outputDirectory = SemantifyrVerifierTestHelper.testArtifactRoot(SpinSemanticConformanceTest::class.java),
            expectedVerdict = VerificationVerdict.Inconclusive,
        )
    }

    @ParameterizedTest
    @MethodSource
    suspend fun `a whole-delay trace of a model whose clock tests are not all closed is the tagged verdict or Inconclusive`(verificationCase: VerificationCase) {
        val portfolio = SingleBackendPortfolio(SpinBackend(), SpinConfig.SafeDfs)
        assumePortfolioAvailable(portfolio)
        val outcome = helper.runVerificationCase(
            context,
            verificationCase,
            portfolio,
            SemantifyrVerifierTestHelper.testArtifactRoot(SpinSemanticConformanceTest::class.java),
            timeout = 1.minutes,
        )
        val result = outcome.report.result
        assertThat(result.verdict)
            .describedAs("${verificationCase.qualifiedName} (${result.message ?: "no message"})")
            .isIn(helper.expectedVerdictFromTags(verificationCase), VerificationVerdict.Inconclusive)
        tracesFound += result.verdict == helper.expectedVerdictFromTags(verificationCase)
    }
}
