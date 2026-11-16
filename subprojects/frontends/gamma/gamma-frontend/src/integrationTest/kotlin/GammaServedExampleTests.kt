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
import hu.bme.mit.semantifyr.portfolios.testing.liveBackends
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class GammaServedExampleTests {

    companion object {
        private val helper = GammaFrontendTestHelper()
        private val artifactRoot = GammaFrontendTestHelper.testArtifactRoot(GammaServedExampleTests::class.java)

        @JvmStatic
        fun `Served Cases Reach Their Expected Verdict On Live's Backends`(): List<GammaCorpusEntry> {
            val manifest = GammaCorpus.load(GammaCorpus.examples)
            return ServedExamples.load().of(ExampleLanguage.Gamma).flatMap { example ->
                example.cases.map { name ->
                    manifest.single {
                        it.model.file == example.path.fileName.toString() && it.case.name == name
                    }
                }
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    suspend fun `Served Cases Reach Their Expected Verdict On Live's Backends`(entry: GammaCorpusEntry) {
        val gammaVerificationCase = helper.discoverInWorkspace(entry.directory, entry.model.file).single {
            it.declaration.name == entry.case.name
        }
        val frontend = helper.buildFrontend(Portfolios.SmartFull, artifactRoot, environment = liveBackends)
        helper.checkVerificationCase(frontend, gammaVerificationCase, entry.case.expectedVerdict)
    }
}
