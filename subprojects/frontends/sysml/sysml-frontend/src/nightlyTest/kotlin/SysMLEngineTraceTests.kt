/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.sysml

import hu.bme.mit.semantifyr.frontends.sysml.testing.CorpusEntry
import hu.bme.mit.semantifyr.frontends.sysml.testing.SysMLCorpus
import hu.bme.mit.semantifyr.frontends.sysml.testing.SysMLv2FrontendTestHelper
import hu.bme.mit.semantifyr.portfolios.Portfolios
import hu.bme.mit.semantifyr.verifier.assumeBackendAvailable
import hu.bme.mit.semantifyr.verifier.portfolio.VerificationPortfolio
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Named
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

// SmartFull lets Uppaal win, so the other engines' witnesses reach the trace command only through single-engine portfolios
class SysMLEngineTraceTests {
    companion object {
        private val helper = SysMLv2FrontendTestHelper()
        private val artifactRoot = SysMLv2FrontendTestHelper.testArtifactRoot(SysMLEngineTraceTests::class.java)

        private val portfolios = listOf(
            Portfolios.ThetaCegarExpl,
            Portfolios.NuxmvIc3Invar,
            Portfolios.SpinExhaustiveDfs,
        )

        private val cases = mapOf(
            "crossroads.sysml" to setOf("TrafficLightABehaviorNormalGreenUnreachableViolated"),
            "door_access.sysml" to setOf("OpenReachable", "OpenUnreachableViolated"),
        )

        @JvmStatic
        fun `Every Engine's Witness Yields A SysML Trace The Fork Validates`(): List<Arguments> {
            val entries = SysMLCorpus.load().entries.filter {
                it.case.name in cases[it.model.file].orEmpty()
            }
            return portfolios.flatMap { portfolio ->
                entries.map {
                    Arguments.of(Named.of(portfolio.id, portfolio), it)
                }
            }
        }
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource
    suspend fun `Every Engine's Witness Yields A SysML Trace The Fork Validates`(portfolio: VerificationPortfolio, entry: CorpusEntry) {
        portfolio.executorKeys.forEach {
            assumeBackendAvailable(it)
        }
        val frontend = helper.buildFrontend(entry, portfolio, artifactRoot.resolve(portfolio.id))

        assertThat(helper.checkSysMLTrace(frontend, entry)).isNotNull()
    }
}
