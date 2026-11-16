/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.portfolios

import hu.bme.mit.semantifyr.oxsts.lang.OxstsStandaloneSetup
import hu.bme.mit.semantifyr.portfolios.testing.ExampleLanguage
import hu.bme.mit.semantifyr.portfolios.testing.ServedExample
import hu.bme.mit.semantifyr.portfolios.testing.ServedExamples
import hu.bme.mit.semantifyr.portfolios.testing.liveBackends
import hu.bme.mit.semantifyr.verifier.CaseManifest
import hu.bme.mit.semantifyr.verifier.SemantifyrVerifierTestHelper
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import kotlin.io.path.name
import kotlin.time.Duration.Companion.minutes

class OxstsServedExampleTests {

    companion object {
        private val injector = OxstsStandaloneSetup().createInjectorAndDoEMFRegistration()
        private val helper = injector.getInstance(SemantifyrVerifierTestHelper::class.java)

        @JvmStatic
        fun `Served Cases Reach Their Expected Verdict On Live's Backends`(): List<Arguments> {
            return ServedExamples.load().of(ExampleLanguage.Oxsts).flatMap { example ->
                example.cases.map {
                    Arguments.of(example, it)
                }
            }
        }
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource
    suspend fun `Served Cases Reach Their Expected Verdict On Live's Backends`(example: ServedExample, caseName: String) {
        val path = example.path
        val context = helper.semantifyrLoader.loadStandaloneModel(path)
        val case = helper.collectVerificationCases(context).single {
            it.qualifiedName.substringAfterLast("::") == caseName
        }
        helper.checkTestModel(
            context,
            case,
            verificationPortfolio = Portfolios.SmartFull,
            outputDirectory = SemantifyrVerifierTestHelper.testArtifactRoot(OxstsServedExampleTests::class.java).resolve(example.id),
            timeout = 2.minutes,
            environment = liveBackends,
            expectedVerdict = CaseManifest.load(path.parent).expectedVerdict(path.name, caseName),
        )
    }
}
