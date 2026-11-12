/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import hu.bme.mit.semantifyr.backend.VerificationVerdict
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.FiredTransitionExpression
import hu.bme.mit.semantifyr.frontends.gamma.testing.GammaFrontendTestHelper
import hu.bme.mit.semantifyr.frontends.gamma.testing.GammaTestEngine
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.io.path.Path

class GammaLevelCrossingTraceTest {

    private val helper = GammaFrontendTestHelper()

    @Test
    suspend fun `the failing barrier case yields a Gamma trace linked to the model`() {
        val gammaVerificationCase = helper.discover(Path("build", "test-models", "LevelCrossing.gamma")).single {
            it.qualifiedName.endsWith("BarrierDownWhileTrainInCrossing")
        }
        val frontend = helper.buildFrontend(
            portfolio = GammaTestEngine.Theta.portfolio,
            outputDirectory = GammaFrontendTestHelper.testArtifactRoot(GammaLevelCrossingTraceTest::class.java),
        )

        val witness = helper.checkVerificationCase(frontend, gammaVerificationCase, VerificationVerdict.Failed).witness

        assertThat(witness).describedAs("A failed AG case has a witness").isNotNull()
        val gammaTrace = helper.readGammaTrace(gammaVerificationCase, witness!!)
        val witnessState = witness.trace.witnessState
        assertThat(gammaTrace.steps).hasSize(1 + witnessState.steps.size)
        assertThat(gammaTrace.steps.flatMap { it.asserts }.filterIsInstance<FiredTransitionExpression>()).isNotEmpty()
    }
}
