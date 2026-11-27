/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.verifier

import com.google.inject.Inject
import com.google.inject.Injector
import hu.bme.mit.semantifyr.backend.BackendVerificationRequest
import hu.bme.mit.semantifyr.backend.BackendVerificationResult
import hu.bme.mit.semantifyr.backend.VerificationVerdict
import hu.bme.mit.semantifyr.backend.execution.AvailabilityReport
import hu.bme.mit.semantifyr.backend.execution.ExecutionEnvironment
import hu.bme.mit.semantifyr.backend.testModel
import hu.bme.mit.semantifyr.oxsts.lang.tests.InjectWithOxsts
import hu.bme.mit.semantifyr.verifier.portfolio.ConcurrencyGate
import hu.bme.mit.semantifyr.verifier.portfolio.PortfolioLeg
import hu.bme.mit.semantifyr.verifier.portfolio.VerificationPortfolio
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.opentest4j.AssertionFailedError
import org.opentest4j.TestAbortedException
import java.nio.file.Path
import kotlin.io.path.writeText

@InjectWithOxsts
class SemantifyrVerifierTestHelperTest {

    @Inject
    private lateinit var helper: SemantifyrVerifierTestHelper

    @TempDir
    lateinit var output: Path

    @TempDir
    lateinit var modelDirectory: Path

    @ParameterizedTest
    @EnumSource(
        VerificationVerdict::class,
        names = [
            "Failed",
            "Inconclusive",
            "Timeout",
            "Errored",
            "NotSupported",
        ],
    )
    suspend fun `a verdict other than the expected one fails`(verdict: VerificationVerdict) {
        assertThrows<AssertionFailedError> {
            check(FixedPortfolio(verdict))
        }
    }

    @Test
    suspend fun `the expected verdict passes`() {
        check(FixedPortfolio(VerificationVerdict.Passed))
    }

    @Test
    suspend fun `an unavailable portfolio skips`() {
        assertThrows<TestAbortedException> {
            check(FixedPortfolio(VerificationVerdict.Passed, available = false))
        }
    }

    @Test
    suspend fun `a missing witness fails where the case expects one`() {
        val failure = assertThrows<AssertionError> {
            check(FixedPortfolio(VerificationVerdict.Passed), validationPortfolio = FixedPortfolio(VerificationVerdict.Passed))
        }

        assertThat(failure).hasMessageContaining("must carry a witness")
    }

    @Test
    suspend fun `a case that expects no witness passes without one`() {
        check(
            FixedPortfolio(VerificationVerdict.Failed),
            caseName = "StaysAtOne",
            validationPortfolio = FixedPortfolio(VerificationVerdict.Passed),
        )
    }

    @Test
    fun `the witness cases are the passed reachabilities and the failed invariants`() {
        val context = helper.semantifyrLoader.loadStandaloneModel(writeModel())
        val names = helper.collectVerificationCases(context, helper.casesExpectingWitness).map {
            it.qualifiedName.substringAfterLast("::")
        }
        assertThat(names).containsExactlyInAnyOrder(
            "Incrementing",
            "CounterExceedsBound",
        )
    }

    private suspend fun check(
        portfolio: VerificationPortfolio,
        caseName: String = "Incrementing",
        validationPortfolio: VerificationPortfolio? = null,
    ) {
        val context = helper.semantifyrLoader.loadStandaloneModel(writeModel())
        val case = helper.collectVerificationCases(context).first {
            it.qualifiedName.endsWith("::$caseName")
        }
        helper.checkTestModel(
            context = context,
            case = case,
            verificationPortfolio = portfolio,
            outputDirectory = output,
            validationPortfolio = validationPortfolio,
        )
    }

    private fun writeModel(): Path {
        val model = modelDirectory.resolve("cases.semantifyr")
        model.writeText(testModel("cases.semantifyr"))
        return model
    }

    private class FixedPortfolio(
        private val verdict: VerificationVerdict,
        private val available: Boolean = true,
    ) : VerificationPortfolio() {
        override val id: String = "fixed"
        override val displayName: String = id
        override val description: String = "test-only"
        override val legs: List<PortfolioLeg<*>> = emptyList()

        override fun availability(environment: ExecutionEnvironment): AvailabilityReport {
            return if (available) {
                AvailabilityReport.Available
            } else {
                AvailabilityReport.Unavailable("fake: absent")
            }
        }

        override suspend fun verify(
            parentInjector: Injector,
            request: BackendVerificationRequest,
            gate: ConcurrencyGate,
            environment: ExecutionEnvironment,
            progress: ProgressContext,
            readBack: suspend (BackendVerificationResult) -> BackendVerificationResult,
        ): BackendVerificationResult {
            return BackendVerificationResult(verdict = verdict, metadata = fakeMetadata(FakeExecutorKey.name))
        }
    }
}
