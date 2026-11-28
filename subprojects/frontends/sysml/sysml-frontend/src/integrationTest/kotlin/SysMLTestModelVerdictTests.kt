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

class SysMLTestModelVerdictTests {
    companion object {
        private val helper = SysMLv2FrontendTestHelper()
        private val artifactRoot = SysMLv2FrontendTestHelper.testArtifactRoot(SysMLTestModelVerdictTests::class.java)

        @JvmStatic
        fun `Test Model Cases Reach Their Expected Verdict`(): List<CorpusEntry> {
            return SysMLCorpus.entries(SysMLv2FrontendTestHelper.TESTS_ROOT).filter {
                it.case.tier != CorpusTier.Nightly
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    suspend fun `Test Model Cases Reach Their Expected Verdict`(entry: CorpusEntry) {
        val frontend = helper.buildFrontend(entry.modelPath, Portfolios.SmartFull, artifactRoot)
        helper.checkVerificationCase(frontend, entry)
    }
}
