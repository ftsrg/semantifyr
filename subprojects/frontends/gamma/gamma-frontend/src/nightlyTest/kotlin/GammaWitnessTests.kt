/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import hu.bme.mit.semantifyr.frontends.gamma.testing.GammaCorpusEntry
import hu.bme.mit.semantifyr.frontends.gamma.testing.GammaFrontendTestHelper
import hu.bme.mit.semantifyr.frontends.gamma.testing.GammaTestEngine
import hu.bme.mit.semantifyr.portfolios.Portfolios
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream
import kotlin.time.Duration.Companion.minutes

class GammaWitnessTests {

    companion object {
        private val helper = GammaFrontendTestHelper()
        private val artifactRoot = GammaFrontendTestHelper.testArtifactRoot(GammaWitnessTests::class.java)

        @JvmStatic
        fun `Witnesses Re-verify Through All Backends`(): Stream<Arguments> {
            return helper.witnessCasesOnEveryEngine()
        }
    }

    @ParameterizedTest(name = "{0} on {1}")
    @MethodSource
    suspend fun `Witnesses Re-verify Through All Backends`(entry: GammaCorpusEntry, engine: GammaTestEngine) {
        helper.checkVerificationCaseOn(engine, entry, artifactRoot, timeout = 30.minutes, validationPortfolio = Portfolios.AllAgree)
    }
}
