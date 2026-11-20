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

class SysMLConformanceTests {
    companion object {
        private val helper = SysMLv2FrontendTestHelper()
        private val artifactRoot = SysMLv2FrontendTestHelper.testArtifactRoot(SysMLConformanceTests::class.java)

        // the frontend tests run every other case through the launcher, which selects no library variant
        @JvmStatic
        fun `Cases Of A Library Variant Reach Their Expected Verdict`(): List<CorpusEntry> {
            return SysMLCorpus.load().entries.filter {
                it.model.variant != SysMLv2Variant.Default && it.case.tier != CorpusTier.Nightly
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    suspend fun `Cases Of A Library Variant Reach Their Expected Verdict`(entry: CorpusEntry) {
        val frontend = helper.buildFrontend(entry, Portfolios.SmartFull, artifactRoot)
        helper.checkConformance(frontend, entry)
    }
}
