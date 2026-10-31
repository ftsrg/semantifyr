/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import hu.bme.mit.semantifyr.frontends.sysml.testing.CorpusTier
import hu.bme.mit.semantifyr.frontends.sysml.testing.SysMLCorpus
import hu.bme.mit.semantifyr.frontends.sysml.testing.SysMLv2FrontendTestHelper
import hu.bme.mit.semantifyr.verifier.FakeBackend
import hu.bme.mit.semantifyr.verifier.FakeConfig
import hu.bme.mit.semantifyr.verifier.portfolio.SingleBackendPortfolio
import org.junit.jupiter.api.Test

class SysMLSmokeTest {

    private val helper = SysMLv2FrontendTestHelper()

    @Test
    suspend fun `a sysml case reaches the verifier and its verdict comes back`() {
        val entry = SysMLCorpus.load().entries.single {
            it.case.tier == CorpusTier.Smoke
        }
        val frontend = helper.buildFrontend(
            entry = entry,
            portfolio = SingleBackendPortfolio(FakeBackend.verdict("fake", entry.case.expectedVerdict), FakeConfig()),
            outputDirectory = SysMLv2FrontendTestHelper.testArtifactRoot(SysMLSmokeTest::class.java),
        )

        helper.checkVerificationCase(frontend, entry)
    }
}
