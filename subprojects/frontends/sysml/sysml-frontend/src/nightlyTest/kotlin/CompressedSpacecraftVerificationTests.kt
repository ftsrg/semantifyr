/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.sysml

import hu.bme.mit.semantifyr.frontends.sysml.testing.CorpusEntry
import hu.bme.mit.semantifyr.frontends.sysml.testing.CorpusTier
import hu.bme.mit.semantifyr.frontends.sysml.testing.CorpusVerdict
import hu.bme.mit.semantifyr.frontends.sysml.testing.SysMLCorpus
import hu.bme.mit.semantifyr.frontends.sysml.testing.SysMLv2FrontendTestHelper
import hu.bme.mit.semantifyr.portfolios.Portfolios
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class CompressedSpacecraftVerificationTests {
    companion object {
        private val helper = SysMLv2FrontendTestHelper()
        private val artifactRoot = SysMLv2FrontendTestHelper.testArtifactRoot(CompressedSpacecraftVerificationTests::class.java)

        @JvmStatic
        fun `Compressed Spacecraft Model Cases Reach Their Expected Verdict`(): List<CorpusEntry> {
            return SysMLCorpus.load().entries.filter {
                it.model.file == "compressedspacecraft.sysml" && it.case.tier == CorpusTier.Nightly && it.case.verdict != CorpusVerdict.Unknown
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    suspend fun `Compressed Spacecraft Model Cases Reach Their Expected Verdict`(entry: CorpusEntry) {
        val frontend = helper.buildFrontend(entry, Portfolios.SmartFull, artifactRoot)
        helper.checkConformance(frontend, entry, validationPortfolio = Portfolios.AllAgree)
    }
}
