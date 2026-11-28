/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import hu.bme.mit.semantifyr.frontends.gamma.testing.GammaCorpus
import hu.bme.mit.semantifyr.frontends.gamma.testing.GammaFrontendTestHelper
import hu.bme.mit.semantifyr.portfolios.Portfolios
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class GammaTraceSmokeTest {

    companion object {
        private val helper = GammaFrontendTestHelper()
        private val artifactRoot = GammaFrontendTestHelper.testArtifactRoot(GammaTraceSmokeTest::class.java)
    }

    @Test
    suspend fun `the smoke model's EF case is verified and its witness and Gamma trace written`() {
        val entry = GammaCorpus.load(GammaCorpus.smoke).single {
            it.case.name == "OnReachable"
        }
        val gammaVerificationCase = helper.discoverInWorkspace(entry.directory, entry.model.file).single {
            it.declaration.name == entry.case.name
        }
        val frontend = helper.buildFrontend(
            portfolio = Portfolios.SmartFull,
            outputDirectory = artifactRoot,
        )

        val result = helper.checkVerificationCase(frontend, gammaVerificationCase, entry.case.expectedVerdict)
        val witness = result.witness

        assertThat(witness).describedAs("An EF case that passes has a witness").isNotNull()
        assertThat(witness!!.gammaTracePath).exists()
        val reportTrace = requireNotNull(result.outcome.report.trace)
        assertThat(witness.gammaTrace.steps).hasSize(1 + reportTrace.steps.size)
    }
}
