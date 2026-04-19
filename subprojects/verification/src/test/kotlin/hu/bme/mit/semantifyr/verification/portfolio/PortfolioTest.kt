/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.verification.portfolio

import hu.bme.mit.semantifyr.backend.AvailabilityReport
import hu.bme.mit.semantifyr.backend.ExecutionEnvironment
import hu.bme.mit.semantifyr.backend.VerificationMetrics
import hu.bme.mit.semantifyr.backend.VerificationRequest
import hu.bme.mit.semantifyr.backend.VerificationResult
import hu.bme.mit.semantifyr.backend.VerificationVerdict
import hu.bme.mit.semantifyr.verification.FakeBackend
import hu.bme.mit.semantifyr.verification.ProgressContext
import hu.bme.mit.semantifyr.verification.fakeMetadata
import hu.bme.mit.semantifyr.verification.fakeRequest
import hu.bme.mit.semantifyr.verification.noopExecutor
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Portfolio subclass exposing the protected combinators for tests. */
private class TestVerificationPortfolio(
    override val id: String,
    private val body: suspend TestVerificationPortfolio.(VerificationRequest, BackendExecutor, ExecutionEnvironment, ProgressContext) -> VerificationResult,
) : VerificationPortfolio() {
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
    ): VerificationResult {
        return body(request, executor, environment, progress)
    }

    suspend fun runFirstDecisive(
        executor: BackendExecutor,
        timeout: Duration = Duration.INFINITE,
        progress: ProgressContext = ProgressContext.NoOp,
        block: PortfolioScope.() -> Unit,
    ): VerificationResult? {
        return firstDecisive(executor, timeout, progress, block)
    }

    suspend fun runFirstNDecisive(
        count: Int,
        executor: BackendExecutor,
        timeout: Duration = Duration.INFINITE,
        progress: ProgressContext = ProgressContext.NoOp,
        block: PortfolioScope.() -> Unit,
    ): VerificationResult? {
        return firstNDecisive(count, executor, timeout, progress, block)
    }

    suspend fun runAll(
        executor: BackendExecutor,
        timeout: Duration = Duration.INFINITE,
        progress: ProgressContext = ProgressContext.NoOp,
        block: PortfolioScope.() -> Unit,
    ): VerificationResult? {
        return all(executor, timeout, progress, block)
    }
}

/** DSL helper: register a [FakeBackend] as a parallel job on this scope. */
private fun PortfolioScope.enqueue(backend: FakeBackend, request: VerificationRequest, env: ExecutionEnvironment) {
    async {
        executor.withPermit { backend.verify(Unit, request, env) }
    }
}

private fun inconclusive(message: String): VerificationResult {
    return VerificationResult.inconclusive(
        metadata = fakeMetadata(),
        metrics = VerificationMetrics(),
        message = message,
    )
}

private val emptyEnv = ExecutionEnvironment.Empty

class PortfolioTest {

    @Test
    suspend fun `firstDecisive returns the first decisive verdict`() {
        val slow = FakeBackend.delayed("slow", 200, VerificationVerdict.Passed)
        val fastInconclusive = FakeBackend.delayed("fast-inconc", 10, VerificationVerdict.Inconclusive)
        val midDecisive = FakeBackend.delayed("mid", 50, VerificationVerdict.Failed)

        val portfolio = TestVerificationPortfolio("p") { req, exec, env, _ ->
            runFirstDecisive(exec, timeout = 2.seconds) {
                enqueue(slow, req, env)
                enqueue(fastInconclusive, req, env)
                enqueue(midDecisive, req, env)
            } ?: error("no decisive result")
        }

        val result = portfolio.verify(fakeRequest(), noopExecutor, emptyEnv, ProgressContext.NoOp)

        assertThat(result.verdict).isEqualTo(VerificationVerdict.Failed)
        assertThat(result.metadata.backendId).isEqualTo("mid")
    }

    @Test
    suspend fun `firstNDecisive returns the agreed verdict when decisive results agree`() {
        val a = FakeBackend.verdict("a", VerificationVerdict.Passed)
        val b = FakeBackend.verdict("b", VerificationVerdict.Passed)
        val c = FakeBackend.verdict("c", VerificationVerdict.Passed)

        val portfolio = TestVerificationPortfolio("p") { req, exec, env, _ ->
            runFirstNDecisive(count = 2, exec) {
                enqueue(a, req, env)
                enqueue(b, req, env)
                enqueue(c, req, env)
            } ?: error("expected majority result")
        }

        val result = portfolio.verify(fakeRequest(), noopExecutor, emptyEnv, ProgressContext.NoOp)

        assertThat(result.verdict).isEqualTo(VerificationVerdict.Passed)
    }

    @Test
    suspend fun `firstNDecisive returns null when decisive results disagree`() {
        val a = FakeBackend.verdict("a", VerificationVerdict.Passed)
        val b = FakeBackend.verdict("b", VerificationVerdict.Failed)

        val portfolio = TestVerificationPortfolio("p") { req, exec, env, _ ->
            runFirstNDecisive(count = 2, exec) {
                enqueue(a, req, env)
                enqueue(b, req, env)
            } ?: inconclusive("disagreement")
        }

        val result = portfolio.verify(fakeRequest(), noopExecutor, emptyEnv, ProgressContext.NoOp)

        assertThat(result.verdict).isEqualTo(VerificationVerdict.Inconclusive)
    }

    @Test
    suspend fun `all runs every member and prefers a decisive consensus`() {
        val a = FakeBackend.verdict("a", VerificationVerdict.Inconclusive)
        val b = FakeBackend.verdict("b", VerificationVerdict.Passed)
        val c = FakeBackend.verdict("c", VerificationVerdict.Errored)

        val portfolio = TestVerificationPortfolio("p") { req, exec, env, _ ->
            runAll(exec) {
                enqueue(a, req, env)
                enqueue(b, req, env)
                enqueue(c, req, env)
            } ?: error("expected any result")
        }

        val result = portfolio.verify(fakeRequest(), noopExecutor, emptyEnv, ProgressContext.NoOp)

        assertThat(result.verdict).isEqualTo(VerificationVerdict.Passed)
        assertThat(a.invocations.get()).isEqualTo(1)
        assertThat(b.invocations.get()).isEqualTo(1)
        assertThat(c.invocations.get()).isEqualTo(1)
    }

    @Test
    fun `empty combinator block is rejected`() {
        val portfolio = TestVerificationPortfolio("p") { _, exec, _, _ ->
            runFirstDecisive(exec) {} ?: inconclusive("no jobs")
        }
        assertThatThrownBy {
            runBlocking { portfolio.verify(fakeRequest(), noopExecutor, emptyEnv, ProgressContext.NoOp) }
        }.hasMessageContaining("at least one job")
    }

    @Test
    suspend fun `firstDecisive reports progress for every incoming result`() {
        val a = FakeBackend.delayed("a", 5, VerificationVerdict.Inconclusive)
        val b = FakeBackend.delayed("b", 20, VerificationVerdict.Passed)

        val messages = mutableListOf<String>()
        val progress = object : ProgressContext {
            override fun checkIsCancelled() {}
            override fun reportProgress(message: String) {
                synchronized(messages) { messages += message }
            }
        }

        val portfolio = TestVerificationPortfolio("p") { req, exec, env, prog ->
            runFirstDecisive(exec, progress = prog) {
                enqueue(a, req, env)
                enqueue(b, req, env)
            } ?: error("expected decisive")
        }

        val result = portfolio.verify(fakeRequest(), noopExecutor, emptyEnv, progress)

        assertThat(result.verdict).isEqualTo(VerificationVerdict.Passed)
        assertThat(messages).anyMatch { it.startsWith("result 1/2: ") }
    }
}
