/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import hu.bme.mit.semantifyr.frontends.gamma.GammaVerificationCase
import hu.bme.mit.semantifyr.frontends.gamma.testing.GammaFrontendTestHelper
import hu.bme.mit.semantifyr.portfolios.Portfolios
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.Path
import java.util.stream.Stream
import kotlin.io.path.Path

class SimpleGammaVerificationTests {

    companion object {
        private val helper = GammaFrontendTestHelper()
        private val artifactRoot = GammaFrontendTestHelper.testArtifactRoot(SimpleGammaVerificationTests::class.java)

        private val SIMPLE_MODEL = Path("build", "test-models", "Simple.gamma")

        @JvmStatic
        fun `Simple Model Verification Cases Should Pass`(): Stream<Arguments> {
            return helper.discoverAsArguments(SIMPLE_MODEL)
        }
    }

    @ParameterizedTest
    @MethodSource
    suspend fun `Simple Model Verification Cases Should Pass`(verificationCase: GammaVerificationCase) {
        val frontend = helper.buildFrontend(
            portfolio = Portfolios.SmartFull,
            outputDirectory = artifactRoot,
        )
        helper.checkConformance(frontend, verificationCase, Portfolios.AllAgree)
    }

}
