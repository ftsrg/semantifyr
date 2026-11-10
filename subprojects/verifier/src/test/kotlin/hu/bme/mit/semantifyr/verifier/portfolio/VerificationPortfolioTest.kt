/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.verifier.portfolio

import com.google.inject.Injector
import hu.bme.mit.semantifyr.backend.BackendVerificationRequest
import hu.bme.mit.semantifyr.backend.BackendVerificationResult
import hu.bme.mit.semantifyr.backend.SoundnessDirection
import hu.bme.mit.semantifyr.backend.VerificationVerdict
import hu.bme.mit.semantifyr.backend.execution.AvailabilityReport
import hu.bme.mit.semantifyr.backend.execution.ExecutionEnvironment
import hu.bme.mit.semantifyr.backend.execution.ExecutorKey
import hu.bme.mit.semantifyr.verifier.FakeBackend
import hu.bme.mit.semantifyr.verifier.FakeConfig
import hu.bme.mit.semantifyr.verifier.ProgressContext
import hu.bme.mit.semantifyr.verifier.fakeMetadata
import hu.bme.mit.semantifyr.verifier.fakeRequest
import hu.bme.mit.semantifyr.verifier.noopGate
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private class TestPortfolio(
    override val id: String = "p",
) : VerificationPortfolio() {
    override val displayName = id
    override val description = "test portfolio"
    override val executorKeys: Set<ExecutorKey<*>> = emptySet()
    override val legs: List<PortfolioLeg> = emptyList()

    override fun availability(environment: ExecutionEnvironment): AvailabilityReport {
        return AvailabilityReport.Available
    }

    override suspend fun verify(
        parentInjector: Injector,
        request: BackendVerificationRequest,
        gate: ConcurrencyGate,
        environment: ExecutionEnvironment,
        progress: ProgressContext,
    ): BackendVerificationResult {
        error("TestPortfolio.verify is unused; tests call runFirstDecisive / runFirstNDecisive / runAll directly")
    }

    suspend fun runFirstDecisive(
        gate: ConcurrencyGate,
        timeout: Duration = Duration.INFINITE,
        progress: ProgressContext = ProgressContext.Silent,
        block: PortfolioScope.() -> Unit,
    ): PortfolioOutcome {
        return firstDecisive(gate, timeout, progress, block)
    }

    suspend fun runFirstNDecisive(
        count: Int,
        gate: ConcurrencyGate,
        timeout: Duration = Duration.INFINITE,
        progress: ProgressContext = ProgressContext.Silent,
        block: PortfolioScope.() -> Unit,
    ): PortfolioOutcome {
        return firstNDecisive(count, gate, timeout, progress, block)
    }

    suspend fun runAll(
        gate: ConcurrencyGate,
        timeout: Duration = Duration.INFINITE,
        progress: ProgressContext = ProgressContext.Silent,
        block: PortfolioScope.() -> Unit,
    ): PortfolioOutcome {
        return all(gate, timeout, progress, block)
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
    private val environment = ExecutionEnvironment.Empty
    private val request = fakeRequest()
    private val parentInjector = mock<Injector>()

    private fun PortfolioScope.enqueue(backend: FakeBackend) {
        async {
            gate.withPermit {
                backend.verify(parentInjector, FakeConfig(), request, environment)
            }
        }
    }

    @Test
    fun `firstDecisive returns the first decisive verdict`() = runTest {
        val slow = FakeBackend.delayed("slow", 200, VerificationVerdict.Passed)
        val fastInconclusive = FakeBackend.delayed("fast-inconclusive", 10, VerificationVerdict.Inconclusive)
        val midDecisive = FakeBackend.delayed("mid", 50, VerificationVerdict.Failed)

        val outcome = portfolio.runFirstDecisive(noopGate, timeout = 2.seconds) {
            enqueue(slow)
            enqueue(fastInconclusive)
            enqueue(midDecisive)
        }

        val result = outcome.orFail("no decisive result")
        assertThat(result.verdict).isEqualTo(VerificationVerdict.Failed)
        assertThat(result.metadata.backendId).isEqualTo("mid")
    }

    @Test
    fun `firstDecisive times out when a job is still pending at the timeout`() = runTest {
        val fastInconclusive = FakeBackend.delayed("fast", 10, VerificationVerdict.Inconclusive)
        val slow = FakeBackend.delayed("slow", 10_000, VerificationVerdict.Passed)

        val outcome = portfolio.runFirstDecisive(noopGate, timeout = 1.seconds) {
            enqueue(fastInconclusive)
            enqueue(slow)
        }

        assertThat(outcome).isInstanceOf(PortfolioOutcome.TimedOut::class.java)
        assertThat(outcome.toBackendVerificationResult(Clock.System.now()).verdict).isEqualTo(VerificationVerdict.Timeout)
        assertThat(slow.cancellations.get()).isEqualTo(1)
    }

    @Test
    fun `firstDecisive skips a Timeout job and waits for a decisive one`() = runTest {
        val timedOut = FakeBackend.verdict("timed-out", VerificationVerdict.Timeout)
        val decisive = FakeBackend.delayed("decisive", 10, VerificationVerdict.Failed)

        val outcome = portfolio.runFirstDecisive(noopGate, timeout = 1.seconds) {
            enqueue(timedOut)
            enqueue(decisive)
        }

        assertThat(outcome.orFail("expected decisive").metadata.backendId).isEqualTo("decisive")
    }

    @Test
    suspend fun `firstDecisive times out when every job finished undecided and one timed out`() {
        val inconclusive = FakeBackend.verdict("inconclusive", VerificationVerdict.Inconclusive)
        val timedOut = FakeBackend.verdict("timed-out", VerificationVerdict.Timeout)

        val outcome = portfolio.runFirstDecisive(noopGate) {
            enqueue(inconclusive)
            enqueue(timedOut)
        }

        assertThat(outcome).isInstanceOf(PortfolioOutcome.TimedOut::class.java)
        assertThat((outcome as PortfolioOutcome.TimedOut).reason).contains("timed-out")
    }

    @Test
    suspend fun `firstDecisive yields NoDecision when every job finished Inconclusive`() {
        val first = FakeBackend.verdict("first", VerificationVerdict.Inconclusive)
        val second = FakeBackend.verdict("second", VerificationVerdict.Inconclusive)

        val outcome = portfolio.runFirstDecisive(noopGate) {
            enqueue(first)
            enqueue(second)
        }

        assertThat(outcome).isInstanceOf(PortfolioOutcome.NoDecision::class.java)
        assertThat(outcome.toBackendVerificationResult(Clock.System.now()).verdict).isEqualTo(VerificationVerdict.Inconclusive)
    }

    @Test
    fun `firstDecisive waits past a Passed from an under-approximating leg`() = runTest {
        val underApproximating = FakeBackend.delayed("under", 10, VerificationVerdict.Passed, SoundnessDirection.UnderApproximating)
        val exact = FakeBackend.delayed("exact", 50, VerificationVerdict.Failed)

        val outcome = portfolio.runFirstDecisive(noopGate, timeout = 2.seconds) {
            enqueue(underApproximating)
            enqueue(exact)
        }

        val result = outcome.orFail("expected the exact leg to decide")
        assertThat(result.verdict).isEqualTo(VerificationVerdict.Failed)
        assertThat(result.metadata.backendId).isEqualTo("exact")
    }

    @Test
    fun `firstDecisive waits past a Failed from an over-approximating leg`() = runTest {
        val overApproximating = FakeBackend.delayed("over", 10, VerificationVerdict.Failed, SoundnessDirection.OverApproximating)
        val exact = FakeBackend.delayed("exact", 50, VerificationVerdict.Passed)

        val outcome = portfolio.runFirstDecisive(noopGate, timeout = 2.seconds) {
            enqueue(overApproximating)
            enqueue(exact)
        }

        assertThat(outcome.orFail("expected the exact leg to decide").metadata.backendId).isEqualTo("exact")
    }

    @Test
    suspend fun `firstDecisive settles on a Failed from an under-approximating leg`() {
        val underApproximating = FakeBackend.verdict("under", VerificationVerdict.Failed, SoundnessDirection.UnderApproximating)

        val outcome = portfolio.runFirstDecisive(noopGate) {
            enqueue(underApproximating)
        }

        assertThat(outcome.orFail("expected a trusted Failed").verdict).isEqualTo(VerificationVerdict.Failed)
    }

    @Test
    suspend fun `firstDecisive reports Inconclusive with every leg's verdict when no verdict is trusted`() {
        val underApproximating = FakeBackend.verdict("under", VerificationVerdict.Passed, SoundnessDirection.UnderApproximating)
        val undecided = FakeBackend.verdict("undecided", VerificationVerdict.Inconclusive)

        val outcome = portfolio.runFirstDecisive(noopGate) {
            enqueue(underApproximating)
            enqueue(undecided)
        }

        assertThat(outcome).isInstanceOf(PortfolioOutcome.NoDecision::class.java)
        val result = outcome.toBackendVerificationResult(Clock.System.now())
        assertThat(result.verdict).isEqualTo(VerificationVerdict.Inconclusive)
        assertThat(result.message).contains("under=Passed (not trusted, UnderApproximating)", "undecided=Inconclusive")
    }

    @Test
    suspend fun `firstDecisive keeps Timeout when a leg timed out and no verdict is trusted`() {
        val underApproximating = FakeBackend.verdict("under", VerificationVerdict.Passed, SoundnessDirection.UnderApproximating)
        val timedOut = FakeBackend.verdict("timed-out", VerificationVerdict.Timeout)

        val outcome = portfolio.runFirstDecisive(noopGate) {
            enqueue(underApproximating)
            enqueue(timedOut)
        }

        assertThat(outcome).isInstanceOf(PortfolioOutcome.TimedOut::class.java)
        assertThat((outcome as PortfolioOutcome.TimedOut).reason).contains("timed-out", "under=Passed (not trusted, UnderApproximating)")
    }

    @Test
    fun `firstNDecisive counts only trusted verdicts`() = runTest {
        val underApproximating = FakeBackend.delayed("under", 10, VerificationVerdict.Passed, SoundnessDirection.UnderApproximating)
        val exact = FakeBackend.delayed("exact", 50, VerificationVerdict.Failed)

        val outcome = portfolio.runFirstNDecisive(count = 1, noopGate) {
            enqueue(underApproximating)
            enqueue(exact)
        }

        assertThat(outcome.orFail("expected the exact leg to decide").metadata.backendId).isEqualTo("exact")
    }

    @Test
    fun `all times out when nothing is decisive and a job is pending at the timeout`() = runTest {
        val fastInconclusive = FakeBackend.delayed("fast", 10, VerificationVerdict.Inconclusive)
        val slow = FakeBackend.delayed("slow", 10_000, VerificationVerdict.Passed)

        val outcome = portfolio.runAll(noopGate, timeout = 1.seconds) {
            enqueue(fastInconclusive)
            enqueue(slow)
        }

        assertThat(outcome).isInstanceOf(PortfolioOutcome.TimedOut::class.java)
    }

    @Test
    fun `all reports Inconclusive over a NotSupported leg that finished first`() = runTest {
        val notSupported = FakeBackend.delayed("refusing", 10, VerificationVerdict.NotSupported)
        val inconclusive = FakeBackend.delayed("undecided", 100, VerificationVerdict.Inconclusive)

        val outcome = portfolio.runAll(noopGate) {
            enqueue(notSupported)
            enqueue(inconclusive)
        }

        val result = outcome.toBackendVerificationResult(Clock.System.now())
        assertThat(result.verdict).isEqualTo(VerificationVerdict.Inconclusive)
        assertThat(result.message).contains("refusing=NotSupported", "undecided=Inconclusive")
    }

    @Test
    fun `all reports Timeout over an Inconclusive leg that finished first`() = runTest {
        val inconclusive = FakeBackend.delayed("undecided", 10, VerificationVerdict.Inconclusive)
        val timedOut = FakeBackend.delayed("timed-out", 100, VerificationVerdict.Timeout)

        val outcome = portfolio.runAll(noopGate) {
            enqueue(inconclusive)
            enqueue(timedOut)
        }

        val result = outcome.toBackendVerificationResult(Clock.System.now())
        assertThat(result.verdict).isEqualTo(VerificationVerdict.Timeout)
        assertThat(result.message).contains("undecided=Inconclusive", "timed-out=Timeout")
    }

    @Test
    fun `all reports Inconclusive over an Errored leg that finished first`() = runTest {
        val errored = FakeBackend.delayed("broken", 10, VerificationVerdict.Errored)
        val inconclusive = FakeBackend.delayed("undecided", 100, VerificationVerdict.Inconclusive)

        val outcome = portfolio.runAll(noopGate) {
            enqueue(errored)
            enqueue(inconclusive)
        }

        val result = outcome.toBackendVerificationResult(Clock.System.now())
        assertThat(result.verdict).isEqualTo(VerificationVerdict.Inconclusive)
        assertThat(result.message).contains("broken=Errored", "undecided=Inconclusive")
    }

    @Test
    suspend fun `all ignores a Timeout job when the decisive jobs agree`() {
        val timedOut = FakeBackend.verdict("timed-out", VerificationVerdict.Timeout)
        val passed = FakeBackend.verdict("passed", VerificationVerdict.Passed)

        val outcome = portfolio.runAll(noopGate) {
            enqueue(timedOut)
            enqueue(passed)
        }

        assertThat(outcome.orFail("expected decisive").verdict).isEqualTo(VerificationVerdict.Passed)
    }

    @Test
    suspend fun `firstNDecisive returns the agreed verdict when decisive results agree`() {
        val first = FakeBackend.verdict("first", VerificationVerdict.Passed)
        val second = FakeBackend.verdict("second", VerificationVerdict.Passed)
        val third = FakeBackend.verdict("third", VerificationVerdict.Passed)

        val outcome = portfolio.runFirstNDecisive(count = 2, noopGate) {
            enqueue(first)
            enqueue(second)
            enqueue(third)
        }

        val result = outcome.orFail("expected majority result")
        assertThat(result.verdict).isEqualTo(VerificationVerdict.Passed)
    }

    @Test
    suspend fun `firstNDecisive yields NoDecision when decisive results disagree`() {
        val first = FakeBackend.verdict("first", VerificationVerdict.Passed)
        val second = FakeBackend.verdict("second", VerificationVerdict.Failed)

        val outcome = portfolio.runFirstNDecisive(count = 2, noopGate) {
            enqueue(first)
            enqueue(second)
        }

        assertThat(outcome).isInstanceOf(PortfolioOutcome.NoDecision::class.java)
    }

    @Test
    suspend fun `all runs every member and prefers a decisive consensus`() {
        val first = FakeBackend.verdict("first", VerificationVerdict.Inconclusive)
        val second = FakeBackend.verdict("second", VerificationVerdict.Passed)
        val third = FakeBackend.verdict("third", VerificationVerdict.Errored)

        val outcome = portfolio.runAll(noopGate) {
            enqueue(first)
            enqueue(second)
            enqueue(third)
        }

        val result = outcome.orFail("expected any result")
        assertThat(result.verdict).isEqualTo(VerificationVerdict.Passed)
        assertThat(first.invocations.get()).isEqualTo(1)
        assertThat(second.invocations.get()).isEqualTo(1)
        assertThat(third.invocations.get()).isEqualTo(1)
    }

    @Test
    fun `a job that throws OutOfMemoryError does not kill sibling jobs`() = runTest {
        val healthy = FakeBackend.delayed("healthy", 10, VerificationVerdict.Passed)
        val outOfMemory = FakeBackend.throwing("out-of-memory", OutOfMemoryError("fake heap blowup"))

        val outcome = portfolio.runFirstDecisive(noopGate) {
            enqueue(outOfMemory)
            enqueue(healthy)
        }

        val result = outcome.orFail("healthy sibling should decide even when its peer OOMed")
        assertThat(result.verdict).isEqualTo(VerificationVerdict.Passed)
        assertThat(result.metadata.backendId).isEqualTo("healthy")
        assertThat(outOfMemory.invocations.get()).isEqualTo(1)
        assertThat(healthy.invocations.get()).isEqualTo(1)
    }

    @Test
    suspend fun `a job that throws StackOverflowError does not kill sibling jobs`() {
        val healthy = FakeBackend.verdict("healthy", VerificationVerdict.Failed)
        val overflowed = FakeBackend.throwing("overflowed", StackOverflowError("fake recursion"))

        val outcome = portfolio.runFirstDecisive(noopGate) {
            enqueue(overflowed)
            enqueue(healthy)
        }

        val result = outcome.orFail("healthy sibling should decide even when its peer StackOverflowed")
        assertThat(result.verdict).isEqualTo(VerificationVerdict.Failed)
    }

    @Test
    suspend fun `all jobs throwing fatal errors map to AllErrored outcome`() {
        val first = FakeBackend.throwing("first", OutOfMemoryError("first"))
        val second = FakeBackend.throwing("second", StackOverflowError("second"))

        val outcome = portfolio.runFirstDecisive(noopGate) {
            enqueue(first)
            enqueue(second)
        }

        assertThat(outcome).isInstanceOf(PortfolioOutcome.AllErrored::class.java)
    }

    @Test
    suspend fun `firstDecisive is Errored when every job returns or throws Errored`() {
        val returned = FakeBackend.verdict("returned", VerificationVerdict.Errored)
        val thrown = FakeBackend.throwing("thrown", IllegalStateException("broken"))

        val outcome = portfolio.runFirstDecisive(noopGate) {
            enqueue(returned)
            enqueue(thrown)
        }

        assertThat(outcome).isInstanceOf(PortfolioOutcome.AllErrored::class.java)
        val result = outcome.toBackendVerificationResult(Clock.System.now())
        assertThat(result.verdict).isEqualTo(VerificationVerdict.Errored)
        assertThat(result.message).contains("All portfolio jobs errored", "(+1 more)")
    }

    @Test
    suspend fun `all is Errored when every job returns Errored`() {
        val first = FakeBackend.verdict("first", VerificationVerdict.Errored)
        val second = FakeBackend.verdict("second", VerificationVerdict.Errored)

        val outcome = portfolio.runAll(noopGate) {
            enqueue(first)
            enqueue(second)
        }

        assertThat(outcome).isInstanceOf(PortfolioOutcome.AllErrored::class.java)
    }

    @Test
    suspend fun `firstDecisive is Inconclusive when an Errored job has an Inconclusive sibling`() {
        val errored = FakeBackend.verdict("errored", VerificationVerdict.Errored)
        val inconclusive = FakeBackend.verdict("inconclusive", VerificationVerdict.Inconclusive)

        val outcome = portfolio.runFirstDecisive(noopGate) {
            enqueue(errored)
            enqueue(inconclusive)
        }

        assertThat(outcome).isInstanceOf(PortfolioOutcome.NoDecision::class.java)
    }

    @Test
    suspend fun `empty combinator block is rejected`() {
        val exception = runCatching {
            portfolio.runFirstDecisive(noopGate) {}
        }.exceptionOrNull()
        assertThat(exception).isNotNull.hasMessageContaining("at least one job")
    }

    @Test
    fun `firstDecisive cancels remaining jobs once a verdict is reached`() = runTest {
        val winner = FakeBackend.delayed("winner", 10, VerificationVerdict.Passed)
        val laggard = FakeBackend.delayed("laggard", 10_000, VerificationVerdict.Passed)

        val outcome = portfolio.runFirstDecisive(noopGate) {
            enqueue(winner)
            enqueue(laggard)
        }

        val result = outcome.orFail("expected decisive")
        assertThat(result.metadata.backendId).isEqualTo("winner")
        assertThat(laggard.cancellations.get()).isEqualTo(1)
    }

    @Test
    suspend fun `all reports AllNotSupported when every job is NotSupported`() {
        val first = FakeBackend.verdict("first", VerificationVerdict.NotSupported)
        val second = FakeBackend.verdict("second", VerificationVerdict.NotSupported)

        val outcome = portfolio.runAll(noopGate) {
            enqueue(first)
            enqueue(second)
        }

        assertThat(outcome).isInstanceOf(PortfolioOutcome.AllNotSupported::class.java)
    }

    @Test
    suspend fun `firstDecisive names every leg and the constructs it reported when every leg is NotSupported`() {
        val theta = notSupported("theta", "theta does not support line 3: for-operations")
        val spin = notSupported("spin", "spin does not support line 5: variables of type real (speed)")

        val outcome = portfolio.runFirstDecisive(noopGate) {
            enqueue(theta)
            enqueue(spin)
        }

        val result = outcome.toBackendVerificationResult(Clock.System.now())
        assertThat(result.verdict).isEqualTo(VerificationVerdict.NotSupported)
        assertThat(result.message).contains(
            "theta (theta does not support line 3: for-operations)",
            "spin (spin does not support line 5: variables of type real (speed))",
        )
    }

    @Test
    suspend fun `all names every leg and the constructs it reported when every leg is NotSupported`() {
        val theta = notSupported("theta", "theta does not support line 3: for-operations")
        val nuxmv = notSupported("nuxmv", "nuxmv does not support line 4: unbounded integers")

        val outcome = portfolio.runAll(noopGate) {
            enqueue(theta)
            enqueue(nuxmv)
        }

        val result = outcome.toBackendVerificationResult(Clock.System.now())
        assertThat(result.verdict).isEqualTo(VerificationVerdict.NotSupported)
        assertThat(result.message).contains(
            "theta (theta does not support line 3: for-operations)",
            "nuxmv (nuxmv does not support line 4: unbounded integers)",
        )
    }

    private fun notSupported(id: String, message: String): FakeBackend {
        return FakeBackend(id) {
            BackendVerificationResult(
                verdict = VerificationVerdict.NotSupported,
                metadata = fakeMetadata(id),
                message = message,
            )
        }
    }

    @Test
    suspend fun `all reports Errored naming both legs when trusted verdicts disagree`() {
        val pass = FakeBackend.verdict("pass", VerificationVerdict.Passed)
        val fail = FakeBackend.verdict("fail", VerificationVerdict.Failed, SoundnessDirection.UnderApproximating)

        val outcome = portfolio.runAll(noopGate) {
            enqueue(pass)
            enqueue(fail)
        }

        val result = outcome.orFail("expected aggregated result")
        assertThat(result.verdict).isEqualTo(VerificationVerdict.Errored)
        assertThat(result.message).contains("pass=Passed (Exact)", "fail=Failed (UnderApproximating)")
    }

    @Test
    suspend fun `all resolves a disagreement explained by the directions in favour of the trusted leg`() {
        val trusted = FakeBackend.verdict("trusted", VerificationVerdict.Failed)
        val untrusted = FakeBackend.verdict("untrusted", VerificationVerdict.Passed, SoundnessDirection.UnderApproximating)

        val outcome = portfolio.runAll(noopGate) {
            enqueue(trusted)
            enqueue(untrusted)
        }

        val result = outcome.orFail("expected the trusted verdict")
        assertThat(result.verdict).isEqualTo(VerificationVerdict.Failed)
        assertThat(result.metadata.backendId).isEqualTo("trusted")
        assertThat(result.message).contains("untrusted=Passed (not trusted, UnderApproximating)")
    }

    @Test
    suspend fun `all reports Inconclusive with every leg's verdict when no verdict is trusted`() {
        val untrusted = FakeBackend.verdict("untrusted", VerificationVerdict.Passed, SoundnessDirection.UnderApproximating)
        val undecided = FakeBackend.verdict("undecided", VerificationVerdict.Inconclusive)

        val outcome = portfolio.runAll(noopGate) {
            enqueue(untrusted)
            enqueue(undecided)
        }

        val result = outcome.toBackendVerificationResult(Clock.System.now())
        assertThat(result.verdict).isEqualTo(VerificationVerdict.Inconclusive)
        assertThat(result.message).contains("untrusted=Passed (not trusted, UnderApproximating)", "undecided=Inconclusive")
    }

    @Test
    fun `firstDecisive reports progress for every incoming result`() = runTest {
        val first = FakeBackend.delayed("first", 5, VerificationVerdict.Inconclusive)
        val second = FakeBackend.delayed("second", 20, VerificationVerdict.Passed)

        val messages = mutableListOf<String>()
        val progress = object : ProgressContext {
            override fun checkIsCancelled() {}
            override fun reportProgress(message: String) {
                synchronized(messages) { messages += message }
            }
        }

        val outcome = portfolio.runFirstDecisive(noopGate, progress = progress) {
            enqueue(first)
            enqueue(second)
        }

        val result = outcome.orFail("expected decisive")
        assertThat(result.verdict).isEqualTo(VerificationVerdict.Passed)
        assertThat(messages).anyMatch { it.startsWith("result 1/2: ") }
    }
}
