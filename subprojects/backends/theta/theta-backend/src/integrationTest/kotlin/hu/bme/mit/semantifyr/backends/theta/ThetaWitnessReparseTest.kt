/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.theta

import hu.bme.mit.semantifyr.oxsts.lang.OxstsStandaloneSetup
import hu.bme.mit.semantifyr.verifier.ReportValue
import hu.bme.mit.semantifyr.verifier.SemantifyrVerifierTestHelper
import hu.bme.mit.semantifyr.verifier.assumeBackendAvailable
import hu.bme.mit.semantifyr.verifier.portfolio.SingleBackendPortfolio
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.entry
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.math.BigInteger
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
        val checked = helper.checkWitnessReparses(
            modelPath = Path("build/test-models/semantic/tracing.semantifyr"),
            qualifiedName = "semantifyr::verification::lang::tracing::TracedChoiceBranchReach",
            verificationPortfolio = portfolio,
            outputDirectory = outputDirectory,
        )

        assertThat(requireNotNull(checked.report.trace).steps).isNotEmpty().allSatisfy {
            assertThat(it.calls).hasSize(1)
        }
    }

    @Test
    suspend fun `array witness reparses and re-verifies`() {
        val checked = helper.checkWitnessReparses(
            modelPath = Path("build/test-models/semantic/arrays.semantifyr"),
            qualifiedName = "semantifyr::verification::lang::arrays::ArraySlotIndependenceReach",
            verificationPortfolio = portfolio,
            outputDirectory = outputDirectory,
        )

        assertThat(requireNotNull(checked.report.trace).states.last().values).contains(
            entry("root.slots[0]", ReportValue.Integer(BigInteger.ONE)),
            entry("root.slots[1]", ReportValue.Integer(BigInteger.ONE)),
            entry("root.slots[2]", ReportValue.Integer(BigInteger.ONE)),
        )
    }

    @Test
    suspend fun `witness of an array of records sized from a binding reparses and re-verifies`() {
        val checked = helper.checkWitnessReparses(
            modelPath = Path("build/test-models/semantic/records.semantifyr"),
            qualifiedName = "semantifyr::verification::lang::records::RecordArrayRuntimeIndexReach",
            verificationPortfolio = portfolio,
            outputDirectory = outputDirectory,
        )

        assertThat(requireNotNull(checked.report.trace).states.last().values).contains(
            entry(
                "root.store.readings[2]",
                ReportValue.Record(
                    mapOf(
                        "value" to ReportValue.Integer(BigInteger.TWO),
                        "valid" to ReportValue.Bool(true),
                    ),
                ),
            ),
        )
    }
}
