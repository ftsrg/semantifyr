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
import hu.bme.mit.semantifyr.verifier.portfolio.VerificationPortfolio
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.opentest4j.AssertionFailedError
import org.opentest4j.TestAbortedException
import java.nio.file.Path
import kotlin.io.path.Path

private val OtherExecutorKey = ExecutorKey("other") {
    AlwaysAvailableExecutor()
}

@InjectWithOxsts
class SemantifyrVerifierTestHelperTest {

    @Inject
    private lateinit var helper: SemantifyrVerifierTestHelper

    @TempDir
    lateinit var output: Path

    private val requiredFake = setOf(FakeExecutorKey.name)

    @Test
    suspend fun `Inconclusive on a required engine fails instead of skipping`() {
        assertThat(check(FixedPortfolio(VerificationVerdict.Inconclusive), requiredFake))
            .isInstanceOf(AssertionFailedError::class.java)
    }

    @Test
    suspend fun `Errored on a required engine fails`() {
        assertThat(check(FixedPortfolio(VerificationVerdict.Errored), requiredFake))
            .isInstanceOf(AssertionFailedError::class.java)
    }

    @Test
    suspend fun `NotSupported on a required engine fails`() {
        assertThat(check(FixedPortfolio(VerificationVerdict.NotSupported), requiredFake))
            .isInstanceOf(AssertionFailedError::class.java)
    }

    @Test
    suspend fun `Timeout on a required engine skips`() {
        assertThat(check(FixedPortfolio(VerificationVerdict.Timeout), requiredFake))
            .isInstanceOf(TestAbortedException::class.java)
    }

    @Test
    suspend fun `Inconclusive on an engine that is not required skips`() {
        assertThat(check(FixedPortfolio(VerificationVerdict.Inconclusive), emptySet()))
            .isInstanceOf(TestAbortedException::class.java)
    }

    @Test
    suspend fun `Errored on an engine that is not required still fails`() {
        assertThat(check(FixedPortfolio(VerificationVerdict.Errored), emptySet()))
            .isInstanceOf(AssertionFailedError::class.java)
    }

    @Test
    suspend fun `absent engine skips unless it is required`() {
        assertThat(check(FixedPortfolio(VerificationVerdict.Passed, available = false), emptySet()))
            .isInstanceOf(TestAbortedException::class.java)
        assertThat(check(FixedPortfolio(VerificationVerdict.Passed, available = false), requiredFake))
            .isInstanceOf(AssertionFailedError::class.java)
    }

    @Test
    suspend fun `expected decisive verdict passes and an unexpected one fails`() {
        assertThat(check(FixedPortfolio(VerificationVerdict.Passed), requiredFake)).isNull()
        assertThat(check(FixedPortfolio(VerificationVerdict.Failed), requiredFake))
            .isInstanceOf(AssertionFailedError::class.java)
    }

    @Test
    suspend fun `Inconclusive on an engine-limited engine passes`() {
        assertThat(check(FixedPortfolio(VerificationVerdict.Inconclusive), requiredFake, "engine-limit:other,fake")).isNull()
    }

    @Test
    suspend fun `NotSupported on an engine-limited engine passes`() {
        assertThat(check(FixedPortfolio(VerificationVerdict.NotSupported), requiredFake, "engine-limit:fake")).isNull()
    }

    @Test
    suspend fun `wrong decisive verdict on an engine-limited engine fails`() {
        assertThat(check(FixedPortfolio(VerificationVerdict.Failed), requiredFake, "engine-limit:fake"))
            .isInstanceOf(AssertionFailedError::class.java)
    }

    @Test
    suspend fun `correct decisive verdict on an engine-limited engine passes`() {
        assertThat(check(FixedPortfolio(VerificationVerdict.Passed), requiredFake, "engine-limit:fake")).isNull()
    }

    @Test
    suspend fun `Errored and Timeout on an engine-limited engine keep their outcome`() {
        assertThat(check(FixedPortfolio(VerificationVerdict.Errored), requiredFake, "engine-limit:fake"))
            .isInstanceOf(AssertionFailedError::class.java)
        assertThat(check(FixedPortfolio(VerificationVerdict.Timeout), requiredFake, "engine-limit:fake"))
            .isInstanceOf(TestAbortedException::class.java)
    }

    @Test
    suspend fun `Inconclusive on an engine not named by engine-limit fails`() {
        assertThat(check(FixedPortfolio(VerificationVerdict.Inconclusive), requiredFake, "engine-limit:spin,uppaal"))
            .isInstanceOf(AssertionFailedError::class.java)
    }

    @Test
    suspend fun `a portfolio with an unlisted engine is not engine-limited`() {
        val mixed = FixedPortfolio(VerificationVerdict.Inconclusive, executorKeys = setOf(FakeExecutorKey, OtherExecutorKey))
        assertThat(check(mixed, requiredFake, "engine-limit:fake"))
            .isInstanceOf(AssertionFailedError::class.java)
    }

    private suspend fun check(
        portfolio: VerificationPortfolio,
        requiredBackends: Set<String>,
        vararg extraTags: String,
    ): Throwable? {
        val context = helper.semantifyrLoader.loadStandaloneModel(Path("../../oxsts-test-models/simple/simple.oxsts"))
        val case = helper.collectVerificationCases(context).first {
            it.qualifiedName.endsWith("::Incrementing")
        }
        return runCatching {
            helper.checkTestModel(
                context = context,
                case = case.copy(tags = case.tags + extraTags),
                verificationPortfolio = portfolio,
                outputDirectory = output,
                requiredBackends = requiredBackends,
            )
        }.exceptionOrNull()
    }

    private class FixedPortfolio(
        private val verdict: VerificationVerdict,
        private val available: Boolean = true,
        override val executorKeys: Set<ExecutorKey<*>> = setOf(FakeExecutorKey),
    ) : VerificationPortfolio() {
        override val id: String = "fixed"
        override val displayName: String = id
        override val description: String = "test-only"

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
