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
import hu.bme.mit.semantifyr.backend.execution.ExecutorKey
import hu.bme.mit.semantifyr.oxsts.lang.tests.InjectWithOxsts
import hu.bme.mit.semantifyr.verifier.portfolio.ConcurrencyGate
import hu.bme.mit.semantifyr.verifier.portfolio.PortfolioLeg
import hu.bme.mit.semantifyr.verifier.portfolio.VerificationPortfolio
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.opentest4j.AssertionFailedError
import org.opentest4j.TestAbortedException
import java.nio.file.Path
import kotlin.io.path.Path

@InjectWithOxsts
class SemantifyrVerifierTestHelperTest {

    @Inject
    private lateinit var helper: SemantifyrVerifierTestHelper

    @TempDir
    lateinit var output: Path

    private val simpleModel = Path("../../oxsts-test-models/simple/simple.semantifyr")

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
        assertThat(check(FixedPortfolio(verdict)))
            .isInstanceOf(AssertionFailedError::class.java)
    }

    @Test
    suspend fun `the expected verdict passes`() {
        assertThat(check(FixedPortfolio(VerificationVerdict.Passed))).isNull()
    }

    @Test
    suspend fun `an unavailable portfolio skips`() {
        assertThat(check(FixedPortfolio(VerificationVerdict.Passed, available = false)))
            .isInstanceOf(TestAbortedException::class.java)
    }

    @Test
    suspend fun `a missing witness fails where the case expects one`() {
        val failure = check(FixedPortfolio(VerificationVerdict.Passed), validationPortfolio = FixedPortfolio(VerificationVerdict.Passed))
        assertThat(failure)
            .isInstanceOf(AssertionError::class.java)
            .hasMessageContaining("must carry a witness")
    }

    @Test
    suspend fun `a case that expects no witness passes without one`() {
        val failure = check(
            FixedPortfolio(VerificationVerdict.Failed),
            caseName = "StaysAtOne",
            validationPortfolio = FixedPortfolio(VerificationVerdict.Passed),
        )
        assertThat(failure).isNull()
    }

    @Test
    fun `the witness cases are the passed reachabilities and the failed invariants`() {
        val context = helper.semantifyrLoader.loadStandaloneModel(simpleModel)
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
    ): Throwable? {
        val context = helper.semantifyrLoader.loadStandaloneModel(simpleModel)
        val case = helper.collectVerificationCases(context).first {
            it.qualifiedName.endsWith("::$caseName")
        }
        return runCatching {
            helper.checkTestModel(
                context = context,
                case = case,
                verificationPortfolio = portfolio,
                outputDirectory = output,
                validationPortfolio = validationPortfolio,
            )
        }.exceptionOrNull()
    }

    private class FixedPortfolio(
        private val verdict: VerificationVerdict,
        private val available: Boolean = true,
    ) : VerificationPortfolio() {
        override val executorKeys: Set<ExecutorKey<*>> = setOf(FakeExecutorKey)
        override val id: String = "fixed"
        override val displayName: String = id
        override val description: String = "test-only"
        override val legs: List<PortfolioLeg> = emptyList()

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
        ): BackendVerificationResult {
            return BackendVerificationResult(verdict = verdict, metadata = fakeMetadata(FakeExecutorKey.name))
        }
    }
}
