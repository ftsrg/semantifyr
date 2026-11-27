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
import org.junit.jupiter.api.Named
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.Path

class SysMLExampleVerdictTests {
    companion object {
        private val helper = SysMLv2FrontendTestHelper()
        private val artifactRoot = SysMLv2FrontendTestHelper.testArtifactRoot(SysMLExampleVerdictTests::class.java)

        private fun outsideTheNightly(entries: List<CorpusEntry>, root: Path): List<Arguments> {
            // a model a library variant needs is SysMLConformanceTests'
            return entries.filter {
                it.case.tier != CorpusTier.Nightly && it.model.variant == SysMLv2Variant.Default
            }.map {
                Arguments.of(Named.of(it.toString(), it), root.resolve(it.model.file))
            }
        }

        @JvmStatic
        fun `Example And Tutorial Cases Reach Their Expected Verdict`(): List<Arguments> {
            return outsideTheNightly(SysMLCorpus.load().entries, SysMLv2FrontendTestHelper.MODELS_ROOT) + outsideTheNightly(SysMLv2FrontendTestHelper.tutorialEntries, SysMLv2FrontendTestHelper.TUTORIAL_ROOT)
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    suspend fun `Example And Tutorial Cases Reach Their Expected Verdict`(entry: CorpusEntry, model: Path) {
        val frontend = helper.buildFrontend(model, Portfolios.SmartFull, artifactRoot)
        helper.checkVerificationCase(frontend, entry)
    }
}
