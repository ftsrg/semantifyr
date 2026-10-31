/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import hu.bme.mit.semantifyr.frontends.sysml.testing.CorpusEntry
import hu.bme.mit.semantifyr.frontends.sysml.testing.CorpusTier
import hu.bme.mit.semantifyr.frontends.sysml.testing.SysMLCorpus
import hu.bme.mit.semantifyr.frontends.sysml.testing.SysMLv2FrontendTestHelper
import hu.bme.mit.semantifyr.portfolios.Portfolios
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import kotlin.time.Duration.Companion.seconds

class SpacecraftFalsificationTests {
    companion object {
        private val helper = SysMLv2FrontendTestHelper()
        private val artifactRoot = SysMLv2FrontendTestHelper.testArtifactRoot(SpacecraftFalsificationTests::class.java)

        @JvmStatic
        fun `Nightly Falsification Twins Should Fail`(): List<CorpusEntry> {
            return SysMLCorpus.load().entries.filter {
                it.case.falsification && it.case.tier == CorpusTier.Nightly
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    suspend fun `Nightly Falsification Twins Should Fail`(entry: CorpusEntry) {
        val frontend = helper.buildFrontend(entry, Portfolios.ThetaFull, artifactRoot, timeout = 300.seconds)
        helper.checkVerificationCase(frontend, entry)
    }
}
