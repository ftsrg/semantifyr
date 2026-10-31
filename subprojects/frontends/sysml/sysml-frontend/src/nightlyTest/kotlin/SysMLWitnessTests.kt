/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.sysml

import hu.bme.mit.semantifyr.frontends.sysml.testing.CorpusEntry
import hu.bme.mit.semantifyr.frontends.sysml.testing.CorpusTier
import hu.bme.mit.semantifyr.frontends.sysml.testing.SysMLCorpus
import hu.bme.mit.semantifyr.frontends.sysml.testing.SysMLv2FrontendTestHelper
import hu.bme.mit.semantifyr.portfolios.Portfolios
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class SysMLWitnessTests {
    companion object {
        private val helper = SysMLv2FrontendTestHelper()
        private val artifactRoot = SysMLv2FrontendTestHelper.testArtifactRoot(SysMLWitnessTests::class.java)

        @JvmStatic
        fun `Witnesses Re-verify Through All Backends`(): List<CorpusEntry> {
            return SysMLCorpus.load().entries.filter {
                it.case.tier != CorpusTier.Nightly
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    suspend fun `Witnesses Re-verify Through All Backends`(entry: CorpusEntry) {
        val frontend = helper.buildFrontend(entry, Portfolios.SmartFull, artifactRoot)
        helper.checkConformance(frontend, entry, validationPortfolio = Portfolios.AllAgree)
    }
}
