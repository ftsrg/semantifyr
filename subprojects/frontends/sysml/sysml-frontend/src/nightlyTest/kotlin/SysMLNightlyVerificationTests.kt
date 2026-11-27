/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.sysml

import hu.bme.mit.semantifyr.backend.VerificationVerdict
import hu.bme.mit.semantifyr.frontends.sysml.testing.CorpusEntry
import hu.bme.mit.semantifyr.frontends.sysml.testing.CorpusTier
import hu.bme.mit.semantifyr.frontends.sysml.testing.CorpusVerdict
import hu.bme.mit.semantifyr.frontends.sysml.testing.SysMLv2FrontendTestHelper
import hu.bme.mit.semantifyr.portfolios.Portfolios
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class SysMLNightlyVerificationTests {
    companion object {
        private val helper = SysMLv2FrontendTestHelper()
        private val artifactRoot = SysMLv2FrontendTestHelper.testArtifactRoot(SysMLNightlyVerificationTests::class.java)

        @JvmStatic
        fun `Nightly Cases Reach Their Expected Verdict`(): List<CorpusEntry> {
            return SysMLv2FrontendTestHelper.corpusEntries.filter {
                it.case.tier == CorpusTier.Nightly && it.case.verdict != CorpusVerdict.Unknown
            }
        }

        @JvmStatic
        fun `Nightly Cases Without A Known Verdict Reach A Decided Verdict`(): List<CorpusEntry> {
            return SysMLv2FrontendTestHelper.corpusEntries.filter {
                it.case.tier == CorpusTier.Nightly && it.case.verdict == CorpusVerdict.Unknown
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    suspend fun `Nightly Cases Reach Their Expected Verdict`(entry: CorpusEntry) {
        val frontend = helper.buildFrontend(entry, Portfolios.SmartFull, artifactRoot)
        helper.checkConformance(frontend, entry, validationPortfolio = Portfolios.AllAgree)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    suspend fun `Nightly Cases Without A Known Verdict Reach A Decided Verdict`(entry: CorpusEntry) {
        val frontend = helper.buildFrontend(entry, Portfolios.SmartFull, artifactRoot)
        val case = frontend.verificationCases().single {
            it.qualifiedName.substringAfterLast("::") == entry.case.name
        }
        val result = frontend.verify(case).outcome.report.result
        assertThat(result.verdict).describedAs("$entry: ${result.message ?: "no message"}").isIn(
            VerificationVerdict.Passed,
            VerificationVerdict.Failed,
        )
    }
}
