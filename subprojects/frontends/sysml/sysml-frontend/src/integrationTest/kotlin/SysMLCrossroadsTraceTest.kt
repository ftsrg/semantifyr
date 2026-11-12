/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.sysml

import hu.bme.mit.semantifyr.frontends.sysml.testing.SysMLCorpus
import hu.bme.mit.semantifyr.frontends.sysml.testing.SysMLv2FrontendTestHelper
import hu.bme.mit.semantifyr.portfolios.Portfolios
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SysMLCrossroadsTraceTest {

    private val helper = SysMLv2FrontendTestHelper()

    @Test
    suspend fun `a crossroads counterexample yields a SysML trace next to its witness`() {
        val entry = SysMLCorpus.load().entries.single {
            it.model.file == "crossroads.sysml" && it.case.name == CASE
        }
        val frontend = helper.buildFrontend(entry, Portfolios.SmartFull, SysMLv2FrontendTestHelper.testArtifactRoot(SysMLCrossroadsTraceTest::class.java))

        val trace = helper.checkSysMLTrace(frontend, entry)

        assertThat(trace).isNotNull().content().contains("package ${CASE}Trace {")
    }

    companion object {
        private const val CASE = "TrafficLightABehaviorNormalGreenUnreachableViolated"
    }
}
