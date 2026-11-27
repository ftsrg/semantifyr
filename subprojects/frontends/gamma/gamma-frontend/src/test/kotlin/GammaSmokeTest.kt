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
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.io.path.Path

class GammaSmokeTest {

    private val helper = GammaFrontendTestHelper()

    @Test
    suspend fun `a gamma case reaches the verifier and the report of its verification comes back`() {
        val frontend = helper.buildFrontend(
            portfolio = SingleBackendPortfolio(FakeBackend.verdict("fake", VerificationVerdict.Passed), FakeConfig()),
            outputDirectory = GammaFrontendTestHelper.testArtifactRoot(GammaSmokeTest::class.java),
        )
        val case = helper.discover(Path("..", "models", "examples", "Simple.gamma")).first()

        val result = frontend.verify(case)

        val report = result.outcome.report
        assertThat(report.result.verdict).isEqualTo(VerificationVerdict.Passed)
        assertThat(report.verificationCase).endsWith(case.declaration.name)
    }
}
