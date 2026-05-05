/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import hu.bme.mit.semantifyr.frontends.gamma.GammaVerificationCase
import hu.bme.mit.semantifyr.frontends.gamma.semantics.testing.GammaFrontendTestHelper
import hu.bme.mit.semantifyr.portfolios.Portfolios
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream
import kotlin.io.path.Path

class GammaVerificationTests {

    companion object {
        private val helper = GammaFrontendTestHelper()
        private val artifactRoot = GammaFrontendTestHelper.testArtifactRoot(GammaVerificationTests::class.java)

        private val MODELS_ROOT = Path("build", "test-models")
        private val CROSSROADS = MODELS_ROOT.resolve("Crossroads.gamma")
        private val SPACECRAFT = MODELS_ROOT.resolve("Spacecraft.gamma")

        private fun argumentsFor(sourcePath: java.nio.file.Path): Stream<Arguments> {
            return helper.discoverAsArguments(sourcePath)
        }

        @JvmStatic
        fun `Crossroads Model Verification Cases Should Pass`(): Stream<Arguments> {
            return argumentsFor(CROSSROADS)
        }

        @JvmStatic
        fun `Spacecraft Model Verification Cases Should Pass`(): Stream<Arguments> {
            return argumentsFor(SPACECRAFT)
        }
    }

    private suspend fun assertPasses(gammaVerificationCase: GammaVerificationCase) {
        val frontend = helper.buildFrontend(
            portfolio = Portfolios.SmartFull,
            outputDirectory = artifactRoot,
        )
        helper.checkVerificationCase(frontend, gammaVerificationCase)
        // TODO: Re-enable witness re-validation once the back-annotated witness type-checks.
    }

    @ParameterizedTest
    @MethodSource
    suspend fun `Crossroads Model Verification Cases Should Pass`(gammaVerificationCase: GammaVerificationCase) {
        assertPasses(gammaVerificationCase)
    }

    @ParameterizedTest
    @MethodSource
    suspend fun `Spacecraft Model Verification Cases Should Pass`(gammaVerificationCase: GammaVerificationCase) {
        assertPasses(gammaVerificationCase)
    }
}
