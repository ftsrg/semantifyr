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
import org.junit.jupiter.api.Named
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.Path
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension
import kotlin.time.Duration.Companion.minutes

class OxstsServedExampleTests {

    companion object {
        private val injector = OxstsStandaloneSetup().createInjectorAndDoEMFRegistration()
        private val helper = injector.getInstance(SemantifyrVerifierTestHelper::class.java)

        // the lessons link into live with a stage's text in the address, so live runs a stage as it runs a served example
        private val lessonStages = ServedExamples.repository.resolve("oxsts-test-models/tutorial")

        @JvmStatic
        fun `Served Cases Reach Their Expected Verdict On Live's Backends`(): List<Arguments> {
            return ServedExamples.load().of(ExampleLanguage.Oxsts).flatMap { example ->
                example.cases.map {
                    Arguments.of(example, it)
                }
            }
        }

        @JvmStatic
        fun `Lesson Stage Cases Reach Their Expected Verdict On Live's Backends`(): List<Arguments> {
            val served = ServedExamples.load().examples.map {
                it.path.normalize()
            }
            return CaseManifest.load(lessonStages).models.filter {
                lessonStages.resolve(it.file).normalize() !in served
            }.flatMap { model ->
                val stage = lessonStages.resolve(model.file)
                model.cases.map {
                    Arguments.of(Named.of(stage.nameWithoutExtension, stage), it.name)
                }
            }
        }
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource
    suspend fun `Served Cases Reach Their Expected Verdict On Live's Backends`(example: ServedExample, caseName: String) {
        verifiesAsExpected(example.path, caseName, example.id)
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource
    suspend fun `Lesson Stage Cases Reach Their Expected Verdict On Live's Backends`(stage: Path, caseName: String) {
        verifiesAsExpected(stage, caseName, stage.nameWithoutExtension)
    }

    private suspend fun verifiesAsExpected(
        path: Path,
        caseName: String,
        artifactDirectory: String,
    ) {
        val context = helper.semantifyrLoader.loadStandaloneModel(path)
        val case = helper.collectVerificationCases(context).single {
            it.qualifiedName.substringAfterLast("::") == caseName
        }
        helper.checkTestModel(
            context,
            case,
            verificationPortfolio = Portfolios.SmartFull,
            outputDirectory = SemantifyrVerifierTestHelper.testArtifactRoot(OxstsServedExampleTests::class.java).resolve(artifactDirectory),
            timeout = 2.minutes,
            environment = liveBackends,
            expectedVerdict = CaseManifest.load(path.parent).expectedVerdict(path.name, caseName),
        )
    }
}
