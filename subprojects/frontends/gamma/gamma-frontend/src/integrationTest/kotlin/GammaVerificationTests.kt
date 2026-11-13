/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import hu.bme.mit.semantifyr.frontends.gamma.testing.GammaCorpus
import hu.bme.mit.semantifyr.frontends.gamma.testing.GammaCorpusEntry
import hu.bme.mit.semantifyr.frontends.gamma.testing.GammaFrontendTestHelper
import hu.bme.mit.semantifyr.portfolios.Portfolios
import hu.bme.mit.semantifyr.portfolios.testing.ExampleLanguage
import hu.bme.mit.semantifyr.portfolios.testing.ServedExamples
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import kotlin.io.path.name

class GammaVerificationTests {

    companion object {
        private val helper = GammaFrontendTestHelper()
        private val artifactRoot = GammaFrontendTestHelper.testArtifactRoot(GammaVerificationTests::class.java)

        // a served case is verified by the served-case test alone
        @JvmStatic
        fun `Example Cases Reach Their Expected Verdict`(): List<GammaCorpusEntry> {
            val served = ServedExamples.load().of(ExampleLanguage.Gamma)
            return GammaCorpus.load(GammaCorpus.examples).filter { entry ->
                served.none {
                    it.path.name == entry.model.file && entry.case.name in it.cases
                }
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    suspend fun `Example Cases Reach Their Expected Verdict`(entry: GammaCorpusEntry) {
        val gammaVerificationCase = helper.discoverInWorkspace(entry.directory, entry.model.file).single {
            it.declaration.name == entry.case.name
        }
        val frontend = helper.buildFrontend(
            portfolio = Portfolios.SmartFull,
            outputDirectory = artifactRoot,
        )
        helper.checkVerificationCase(frontend, gammaVerificationCase, entry.case.expectedVerdict)
    }

    @Test
    suspend fun `A passing EF case returns its witness with a Gamma trace`() {
        val entry = GammaCorpus.load(GammaCorpus.examples).single {
            it.case.name == "LeaderStatechartIdleReachable"
        }
        val gammaVerificationCase = helper.discoverInWorkspace(entry.directory, entry.model.file).single {
            it.declaration.name == entry.case.name
        }
        val frontend = helper.buildFrontend(
            portfolio = Portfolios.SmartFull,
            outputDirectory = artifactRoot,
        )

        val witness = helper.checkVerificationCase(frontend, gammaVerificationCase, entry.case.expectedVerdict).witness

        assertThat(witness).describedAs("An EF case that passes has a witness").isNotNull()
        assertThat(witness!!.gammaTracePath).exists()
        assertThat(witness.gammaTrace.steps).hasSize(1 + witness.trace.witnessState.steps.size)
    }
}
