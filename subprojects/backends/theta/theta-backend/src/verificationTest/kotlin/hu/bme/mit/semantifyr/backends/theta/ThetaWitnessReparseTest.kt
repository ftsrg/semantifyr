/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.theta

import hu.bme.mit.semantifyr.oxsts.lang.OxstsStandaloneSetup
import hu.bme.mit.semantifyr.verifier.SemantifyrVerifierTestHelper
import hu.bme.mit.semantifyr.verifier.assumeBackendAvailable
import hu.bme.mit.semantifyr.verifier.portfolio.SingleBackendPortfolio
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.io.path.Path

class ThetaWitnessReparseTest {

    companion object {

        private val injector = OxstsStandaloneSetup().createInjectorAndDoEMFRegistration()
        private val helper = injector.getInstance(SemantifyrVerifierTestHelper::class.java)

        @JvmStatic
        @BeforeAll
        fun assumeThetaAvailable() {
            assumeBackendAvailable(ThetaExecutorKey)
        }
    }

    private val portfolio = SingleBackendPortfolio(ThetaBackend(), ThetaConfig.CegarExplPredCombined)
    private val outputDirectory = SemantifyrVerifierTestHelper.testArtifactRoot(ThetaWitnessReparseTest::class.java)

    @Test
    suspend fun `traced choice branch witness reparses and re-verifies`() {
        val result = helper.checkWitnessReparses(
            modelPath = Path("build/test-models/semantic/tracing.oxsts"),
            qualifiedName = "semantifyr::verification::lang::tracing::TracedChoiceBranchReach",
            verificationPortfolio = portfolio,
            outputDirectory = outputDirectory,
        )

        val callTrace = requireNotNull(result.trace).callTrace
        assertThat(callTrace.initialStep.traces).isEmpty()
        assertThat(callTrace.steps).isNotEmpty().allSatisfy {
            assertThat(it.traces).hasSize(1)
        }
    }

    @Test
    suspend fun `array witness reparses and re-verifies`() {
        val result = helper.checkWitnessReparses(
            modelPath = Path("build/test-models/semantic/arrays.oxsts"),
            qualifiedName = "semantifyr::verification::lang::arrays::ArraySlotIndependenceReach",
            verificationPortfolio = portfolio,
            outputDirectory = outputDirectory,
        )

        val finalState = requireNotNull(result.trace).witnessState.steps.last().values.map {
            it.variable to it.value
        }
        assertThat(finalState).contains(
            "root.slots[0]" to "1",
            "root.slots[1]" to "1",
            "root.slots[2]" to "1",
        )
    }
}
