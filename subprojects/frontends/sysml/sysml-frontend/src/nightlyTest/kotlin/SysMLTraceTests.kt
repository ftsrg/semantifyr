/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.sysml

import hu.bme.mit.semantifyr.frontends.sysml.testing.CorpusTier
import hu.bme.mit.semantifyr.frontends.sysml.testing.SysMLv2FrontendTestHelper
import hu.bme.mit.semantifyr.portfolios.Portfolios
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.io.path.Path

class SysMLTraceTests {
    companion object {
        private val helper = SysMLv2FrontendTestHelper()
        private val artifactRoot = SysMLv2FrontendTestHelper.testArtifactRoot(SysMLTraceTests::class.java)
        private val guardedFixture = Path("src", "nightlyTest", "resources", "SysMLTraceTests")
    }

    @Test
    suspend fun `Every Witness Of The SysML Suites Yields A SysML Trace The Fork Validates`() {
        val corpus = SysMLv2FrontendTestHelper.corpusEntries.filter {
            it.case.tier != CorpusTier.Nightly
        }.groupBy {
            it.model
        }.values.map {
            helper.buildFrontend(it.first(), Portfolios.SmartFull, artifactRoot.resolve("corpus")) to it
        }
        val guardedModel = SysMLv2FrontendTestHelper.guardedInitialTransitionModel(guardedFixture, artifactRoot.resolve("workspace"))
        val guarded = SysMLv2Variant.entries.map { variant ->
            val frontend = helper.buildFrontend(guardedModel, Portfolios.SmartFull, artifactRoot.resolve(variant.name.lowercase()), variant)
            frontend to SysMLv2FrontendTestHelper.guardedInitialTransitionEntries(guardedFixture, variant)
        }

        val cases = corpus + guarded
        val traces = cases.flatMap { (frontend, entries) ->
            entries.mapNotNull {
                helper.checkSysMLTrace(frontend, it)
            }
        }
        val expectedTraces = cases.sumOf { (frontend, entries) ->
            entries.count {
                helper.expectsWitness(frontend, it)
            }
        }

        assertThat(traces).isNotEmpty().hasSize(expectedTraces)
    }
}
