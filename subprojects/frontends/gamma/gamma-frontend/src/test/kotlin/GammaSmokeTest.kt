/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import hu.bme.mit.semantifyr.backend.VerificationVerdict
import hu.bme.mit.semantifyr.frontends.gamma.testing.GammaFrontendTestHelper
import hu.bme.mit.semantifyr.verifier.FakeBackend
import hu.bme.mit.semantifyr.verifier.FakeConfig
import hu.bme.mit.semantifyr.verifier.portfolio.SingleBackendPortfolio
import org.junit.jupiter.api.Test
import kotlin.io.path.Path

class GammaSmokeTest {

    private val helper = GammaFrontendTestHelper()

    @Test
    suspend fun `a gamma case reaches the verifier and its verdict comes back`() {
        val frontend = helper.buildFrontend(
            portfolio = SingleBackendPortfolio(FakeBackend.verdict("fake", VerificationVerdict.Passed), FakeConfig()),
            outputDirectory = GammaFrontendTestHelper.testArtifactRoot(GammaSmokeTest::class.java),
        )

        helper.checkVerificationCase(frontend, helper.discover(Path("build", "test-models", "Simple.gamma")).first())
    }
}
