/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import hu.bme.mit.semantifyr.frontends.gamma.GammaVerificationCase
import hu.bme.mit.semantifyr.frontends.gamma.testing.GammaFrontendTestHelper
import hu.bme.mit.semantifyr.portfolios.Portfolios
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream
import kotlin.io.path.Path

class GammaWitnessTests {

    companion object {
        private val helper = GammaFrontendTestHelper()
        private val artifactRoot = GammaFrontendTestHelper.testArtifactRoot(GammaWitnessTests::class.java)

        private val MODELS = listOf("Simple.gamma", "Crossroads.gamma", "Spacecraft.gamma")

        @JvmStatic
        fun `Witnesses Re-verify Through All Backends`(): Stream<Arguments> {
            return MODELS.stream().flatMap {
                helper.discoverAsArguments(Path("build", "test-models", it))
            }
        }
    }

    @ParameterizedTest
    @MethodSource
    suspend fun `Witnesses Re-verify Through All Backends`(gammaVerificationCase: GammaVerificationCase) {
        val frontend = helper.buildFrontend(
            portfolio = Portfolios.SmartFull,
            outputDirectory = artifactRoot,
        )
        helper.checkConformance(frontend, gammaVerificationCase, Portfolios.AllAgree)
    }
}
