/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.verification.portfolio

import hu.bme.mit.semantifyr.backend.AvailabilityReport
import hu.bme.mit.semantifyr.backend.BackendVerificationResult
import hu.bme.mit.semantifyr.backend.ExecutionEnvironment
import hu.bme.mit.semantifyr.backend.VerificationRequest
import hu.bme.mit.semantifyr.backend.VerificationVerdict
import hu.bme.mit.semantifyr.verification.FakeBackend
import hu.bme.mit.semantifyr.verification.ProgressContext
import hu.bme.mit.semantifyr.verification.fakeRequest
import hu.bme.mit.semantifyr.verification.noopExecutor
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private class TestPortfolio(override val id: String = "p") : VerificationPortfolio() {
    override val displayName: String = id
    override val description: String = "test portfolio"
    override val familyId: String = "test"

    override fun availability(environment: ExecutionEnvironment): AvailabilityReport {
        return AvailabilityReport.Available
    }

    override suspend fun verify(
        request: VerificationRequest,
        executor: BackendExecutor,
        environment: ExecutionEnvironment,
        progress: ProgressContext,
    ): BackendVerificationResult {
        error("TestPortfolio.verify is unused; tests call runFirstDecisive / runFirstNDecisive / runAll directly")
    }

    suspend fun runFirstDecisive(
        executor: BackendExecutor,
        timeout: Duration = Duration.INFINITE,
        progress: ProgressContext = ProgressContext.NoOp,
        block: PortfolioScope.() -> Unit,
    ): PortfolioOutcome {
        return firstDecisive(executor, timeout, progress, block)
    }

    suspend fun runFirstNDecisive(
        count: Int,
        executor: BackendExecutor,
        timeout: Duration = Duration.INFINITE,
        progress: ProgressContext = ProgressContext.NoOp,
        block: PortfolioScope.() -> Unit,
    ): PortfolioOutcome {
        return firstNDecisive(count, executor, timeout, progress, block)
    }

    suspend fun runAll(
        executor: BackendExecutor,
        timeout: Duration = Duration.INFINITE,
        progress: ProgressContext = ProgressContext.NoOp,
        block: PortfolioScope.() -> Unit,
    ): PortfolioOutcome {
        return all(executor, timeout, progress, block)
    }
}

private fun PortfolioOutcome.orFail(message: String): BackendVerificationResult {
    return when (this) {
        is PortfolioOutcome.Decided -> result
        else -> error(message)
    }
}

class VerificationPortfolioTest {

    private val portfolio = TestPortfolio()
    private val env = ExecutionEnvironment.Empty
    private val request = fakeRequest()

    private fun PortfolioScope.enqueue(backend: FakeBackend) {
        async {
            executor.withPermit {
                backend.verify(Unit, request, env)
            }
        }
    }

    @Test
    fun `firstDecisive returns the first decisive verdict`() = runTest {
        val slow = FakeBackend.delayed("slow", 200, VerificationVerdict.Passed)
        val fastInconclusive = FakeBackend.delayed("fast-inconc", 10, VerificationVerdict.Inconclusive)
        val midDecisive = FakeBackend.delayed("mid", 50, VerificationVerdict.Failed)

        val outcome = portfolio.runFirstDecisive(noopExecutor, timeout = 2.seconds) {
            enqueue(slow)
            enqueue(fastInconclusive)
            enqueue(midDecisive)
        }

        val result = outcome.orFail("no decisive result")
        assertThat(result.verdict).isEqualTo(VerificationVerdict.Failed)
        assertThat(result.metadata.backendId).isEqualTo("mid")
    }

    @Test
    suspend fun `firstNDecisive returns the agreed verdict when decisive results agree`() {
        val a = FakeBackend.verdict("a", VerificationVerdict.Passed)
        val b = FakeBackend.verdict("b", VerificationVerdict.Passed)
        val c = FakeBackend.verdict("c", VerificationVerdict.Passed)

        val outcome = portfolio.runFirstNDecisive(count = 2, noopExecutor) {
            enqueue(a)
            enqueue(b)
            enqueue(c)
        }

        val result = outcome.orFail("expected majority result")
        assertThat(result.verdict).isEqualTo(VerificationVerdict.Passed)
    }

    @Test
    suspend fun `firstNDecisive yields NoDecision when decisive results disagree`() {
        val a = FakeBackend.verdict("a", VerificationVerdict.Passed)
        val b = FakeBackend.verdict("b", VerificationVerdict.Failed)

        val outcome = portfolio.runFirstNDecisive(count = 2, noopExecutor) {
            enqueue(a)
            enqueue(b)
        }

        assertThat(outcome).isInstanceOf(PortfolioOutcome.NoDecision::class.java)
    }

    @Test
    suspend fun `all runs every member and prefers a decisive consensus`() {
        val a = FakeBackend.verdict("a", VerificationVerdict.Inconclusive)
        val b = FakeBackend.verdict("b", VerificationVerdict.Passed)
        val c = FakeBackend.verdict("c", VerificationVerdict.Errored)

        val outcome = portfolio.runAll(noopExecutor) {
            enqueue(a)
            enqueue(b)
            enqueue(c)
        }

        val result = outcome.orFail("expected any result")
        assertThat(result.verdict).isEqualTo(VerificationVerdict.Passed)
        assertThat(a.invocations.get()).isEqualTo(1)
        assertThat(b.invocations.get()).isEqualTo(1)
        assertThat(c.invocations.get()).isEqualTo(1)
    }

    @Test
    fun `a job that throws OutOfMemoryError does not kill sibling jobs`() = runTest {
        val healthy = FakeBackend.delayed("healthy", 10, VerificationVerdict.Passed)
        val oomed = FakeBackend.throwing("oomed", OutOfMemoryError("fake heap blowup"))

        val outcome = portfolio.runFirstDecisive(noopExecutor) {
            enqueue(oomed)
            enqueue(healthy)
        }

        val result = outcome.orFail("healthy sibling should decide even when its peer OOMed")
        assertThat(result.verdict).isEqualTo(VerificationVerdict.Passed)
        assertThat(result.metadata.backendId).isEqualTo("healthy")
        assertThat(oomed.invocations.get()).isEqualTo(1)
        assertThat(healthy.invocations.get()).isEqualTo(1)
    }

    @Test
    suspend fun `a job that throws StackOverflowError does not kill sibling jobs`() {
        val healthy = FakeBackend.verdict("healthy", VerificationVerdict.Failed)
        val overflowed = FakeBackend.throwing("overflowed", StackOverflowError("fake recursion"))

        val outcome = portfolio.runFirstDecisive(noopExecutor) {
            enqueue(overflowed)
            enqueue(healthy)
        }

        val result = outcome.orFail("healthy sibling should decide even when its peer StackOverflowed")
        assertThat(result.verdict).isEqualTo(VerificationVerdict.Failed)
    }

    @Test
    suspend fun `all jobs throwing fatal errors map to AllErrored outcome`() {
        val a = FakeBackend.throwing("a", OutOfMemoryError("a"))
        val b = FakeBackend.throwing("b", StackOverflowError("b"))

        val outcome = portfolio.runFirstDecisive(noopExecutor) {
            enqueue(a)
            enqueue(b)
        }

        assertThat(outcome).isInstanceOf(PortfolioOutcome.AllErrored::class.java)
    }

    @Test
    suspend fun `empty combinator block is rejected`() {
        val exception = runCatching {
            portfolio.runFirstDecisive(noopExecutor) {}
        }.exceptionOrNull()
        assertThat(exception).isNotNull.hasMessageContaining("at least one job")
    }

    @Test
    fun `firstDecisive reports progress for every incoming result`() = runTest {
        val a = FakeBackend.delayed("a", 5, VerificationVerdict.Inconclusive)
        val b = FakeBackend.delayed("b", 20, VerificationVerdict.Passed)

        val messages = mutableListOf<String>()
        val progress = object : ProgressContext {
            override fun checkIsCancelled() {}
            override fun reportProgress(message: String) {
                synchronized(messages) { messages += message }
            }
        }

        val outcome = portfolio.runFirstDecisive(noopExecutor, progress = progress) {
            enqueue(a)
            enqueue(b)
        }

        val result = outcome.orFail("expected decisive")
        assertThat(result.verdict).isEqualTo(VerificationVerdict.Passed)
        assertThat(messages).anyMatch { it.startsWith("result 1/2: ") }
    }
}
