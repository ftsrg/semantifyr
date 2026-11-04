/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.uppaal

import hu.bme.mit.semantifyr.backends.uppaal.transformation.UppaalCapabilityChecker
import hu.bme.mit.semantifyr.backends.uppaal.verification.UppaalBackend
import hu.bme.mit.semantifyr.backends.uppaal.verification.UppaalBackendModule
import hu.bme.mit.semantifyr.backends.uppaal.verification.UppaalConfig
import hu.bme.mit.semantifyr.oxsts.lang.OxstsStandaloneSetup
import hu.bme.mit.semantifyr.verifier.SemantifyrVerifierTestHelper
import hu.bme.mit.semantifyr.verifier.VerificationCase
import hu.bme.mit.semantifyr.verifier.assumeBackendAvailable
import hu.bme.mit.semantifyr.verifier.discovery.CaseFilter
import hu.bme.mit.semantifyr.verifier.portfolio.SingleBackendPortfolio
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import kotlin.io.path.Path

class UppaalSemanticConformanceTest {

    companion object {

        private val injector = OxstsStandaloneSetup().createInjectorAndDoEMFRegistration()
        private val helper = injector.getInstance(SemantifyrVerifierTestHelper::class.java)

        private val undecidedCases = mapOf(
            "semantifyr::verification::lang::boundedness::DoublingReachesNegative" to "Uppaal stops on the range error after 0.07 s: an assignment of 1073741824 outside the declared range (x)",
            "semantifyr::verification::lang::boundedness::DoublingStaysPositive" to "Uppaal stops on the range error after 0.05 s: an assignment of 1073741824 outside the declared range (x)",
            "semantifyr::verification::lang::boundedness::UnboundedCounterBound" to "the proof counts through Uppaal's 31-bit range at 490,000 states and 80 MB a second here, about 37 minutes and 170 GB to the range error at 2^30",
            "semantifyr::verification::lang::local_vars::LocalVarNotPersistedBound" to "the proof counts through Uppaal's 31-bit range for 331 s and 18 GB here, then Uppaal stops on the range error (1073741832 assigned to total)",
        )

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
        fun `OXSTS Simple Test Suite Passes`(): List<Arguments> {
            return helper.collectAcceptedCasesAsArguments(
                context,
                UppaalBackendModule(),
                UppaalCapabilityChecker::class.java,
                SemantifyrVerifierTestHelper.testArtifactRoot(UppaalSemanticConformanceTest::class.java).resolve("capability-check"),
                CaseFilter.Matching {
                    it.qualifiedName !in undecidedCases
                },
            )
        }
    }

    @ParameterizedTest
    @MethodSource
    suspend fun `OXSTS Simple Test Suite Passes`(verificationCase: VerificationCase) {
        helper.checkTestModel(
            context,
            verificationCase,
            verificationPortfolio = SingleBackendPortfolio(UppaalBackend(), UppaalConfig.Default),
            outputDirectory = SemantifyrVerifierTestHelper.testArtifactRoot(UppaalSemanticConformanceTest::class.java),
        )
    }
}
