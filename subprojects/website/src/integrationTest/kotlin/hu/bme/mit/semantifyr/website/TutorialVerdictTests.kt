/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.website

import hu.bme.mit.semantifyr.oxsts.lang.OxstsStandaloneSetup
import hu.bme.mit.semantifyr.portfolios.Portfolios
import hu.bme.mit.semantifyr.verifier.CaseManifest
import hu.bme.mit.semantifyr.verifier.ManifestTier
import hu.bme.mit.semantifyr.verifier.SemantifyrVerifierTestHelper
import org.junit.jupiter.api.Named
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension
import kotlin.time.Duration.Companion.minutes

class TutorialVerdictTests {

    companion object {
        private val injector = OxstsStandaloneSetup().createInjectorAndDoEMFRegistration()
        private val helper = injector.getInstance(SemantifyrVerifierTestHelper::class.java)
        private val tutorial = Path("models", "tutorial")
        private val manifest = CaseManifest.load(tutorial)

        @JvmStatic
        fun `Tutorial Cases Reach Their Expected Verdict`(): List<Arguments> {
            return manifest.models.flatMap { model ->
                val stage = tutorial.resolve(model.file)
                model.cases.filter {
                    it.tier != ManifestTier.Nightly
                }.map {
                    Arguments.of(Named.of(stage.nameWithoutExtension, stage), it.name)
                }
            }
        }
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource
    suspend fun `Tutorial Cases Reach Their Expected Verdict`(stage: Path, caseName: String) {
        val context = helper.semantifyrLoader.loadStandaloneModel(stage)
        val case = helper.collectVerificationCases(context).single {
            it.qualifiedName.substringAfterLast("::") == caseName
        }
        helper.checkTestModel(
            context,
            case,
            verificationPortfolio = Portfolios.SmartFull,
            outputDirectory = SemantifyrVerifierTestHelper.testArtifactRoot(TutorialVerdictTests::class.java).resolve(stage.nameWithoutExtension),
            timeout = 2.minutes,
            expectedVerdict = manifest.expectedVerdict(stage.name, caseName),
        )
    }
}
