/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.sysml

import hu.bme.mit.semantifyr.frontends.sysml.testing.CorpusTier
import hu.bme.mit.semantifyr.frontends.sysml.testing.SysMLCorpus
import hu.bme.mit.semantifyr.frontends.sysml.testing.SysMLv2FrontendTestHelper
import hu.bme.mit.semantifyr.portfolios.Portfolios
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.io.path.nameWithoutExtension

class SysMLTraceTests {
    companion object {
        private val helper = SysMLv2FrontendTestHelper()
        private val artifactRoot = SysMLv2FrontendTestHelper.testArtifactRoot(SysMLTraceTests::class.java)

        // whether a case has a witness follows from its property's root operator and its verdict, which the manifest does not hold: 76 corpus, 4 guarded initial transition and 3 tutorial witnesses among the 118 plus 3 cases the integration suites verify
        private const val EXPECTED_TRACES = 83
    }

    @Test
    suspend fun `Every Witness Of The SysML Suites Yields A SysML Trace The Fork Validates`() {
        val corpus = SysMLCorpus.load().entries.filter {
            it.case.tier != CorpusTier.Nightly
        }.groupBy {
            it.model
        }.values.map {
            helper.buildFrontend(it.first(), Portfolios.SmartFull, artifactRoot.resolve("corpus")) to it
        }
        val guardedModel = SysMLv2FrontendTestHelper.guardedInitialTransitionModel(artifactRoot.resolve("workspace"))
        val guarded = SysMLv2Variant.entries.map { variant ->
            val frontend = helper.buildFrontend(guardedModel, Portfolios.SmartFull, artifactRoot.resolve(variant.name.lowercase()), variant)
            frontend to SysMLv2FrontendTestHelper.guardedInitialTransitionEntries(variant)
        }
        val tutorial = SysMLv2FrontendTestHelper.tutorialEntries.groupBy {
            it.model
        }.map { (model, entries) ->
            val source = SysMLv2FrontendTestHelper.TUTORIAL_ROOT.resolve(model.file)
            helper.buildFrontend(source, Portfolios.SmartFull, artifactRoot.resolve(source.nameWithoutExtension)) to entries
        }

        val traces = (corpus + guarded + tutorial).flatMap { (frontend, entries) ->
            entries.mapNotNull {
                helper.checkSysMLTrace(frontend, it)
            }
        }

        assertThat(traces).hasSize(EXPECTED_TRACES)
    }
}
