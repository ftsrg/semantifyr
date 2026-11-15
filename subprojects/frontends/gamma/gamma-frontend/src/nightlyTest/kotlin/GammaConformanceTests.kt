/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import hu.bme.mit.semantifyr.frontends.gamma.testing.GammaCorpus
import hu.bme.mit.semantifyr.frontends.gamma.testing.GammaCorpusEntry
import hu.bme.mit.semantifyr.frontends.gamma.testing.GammaFrontendTestHelper
import hu.bme.mit.semantifyr.portfolios.Portfolios
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import kotlin.time.Duration.Companion.minutes

class GammaConformanceTests {

    companion object {
        private val helper = GammaFrontendTestHelper()
        private val artifactRoot = GammaFrontendTestHelper.testArtifactRoot(GammaConformanceTests::class.java)

        @JvmStatic
        fun `Corpus Coverage Cases Reach Their Expected Verdict`(): List<GammaCorpusEntry> {
            return GammaCorpus.load(GammaCorpus.corpus)
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    suspend fun `Corpus Coverage Cases Reach Their Expected Verdict`(entry: GammaCorpusEntry) {
        val gammaVerificationCase = helper.discoverInWorkspace(entry.directory, entry.model.file).single {
            it.declaration.name == entry.case.name
        }
        val frontend = helper.buildFrontend(Portfolios.SmartFull, artifactRoot, timeout = 2.minutes)
        helper.checkVerificationCase(frontend, gammaVerificationCase, entry.case.expectedVerdict)
    }
}
