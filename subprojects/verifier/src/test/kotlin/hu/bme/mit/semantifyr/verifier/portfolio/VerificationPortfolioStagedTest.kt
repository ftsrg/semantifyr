/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.verifier.portfolio

import com.google.inject.Injector
import hu.bme.mit.semantifyr.backend.BackendCapabilityChecker
import hu.bme.mit.semantifyr.backend.BackendVerificationRequest
import hu.bme.mit.semantifyr.backend.BackendVerificationResult
import hu.bme.mit.semantifyr.backend.VerificationBackend
import hu.bme.mit.semantifyr.backend.VerificationVerdict
import hu.bme.mit.semantifyr.backend.execution.AvailabilityReport
import hu.bme.mit.semantifyr.backend.execution.BackendExecutor
import hu.bme.mit.semantifyr.backend.execution.ExecutionEnvironment
import hu.bme.mit.semantifyr.backend.execution.ExecutorKey
import hu.bme.mit.semantifyr.verifier.FakeConfig
import hu.bme.mit.semantifyr.verifier.FakeExecutorKey
import hu.bme.mit.semantifyr.verifier.ProgressContext
import hu.bme.mit.semantifyr.verifier.fakeMetadata
import hu.bme.mit.semantifyr.verifier.fakeRequest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import kotlin.coroutines.cancellation.CancellationException
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

private class Answer(
    val after: Duration,
    val verdict: VerificationVerdict,
)

private class RecordingBackend(
    private val now: () -> Duration,
    private val answers: Map<String, Answer>,
    override val executorKey: ExecutorKey<BackendExecutor>,
) : VerificationBackend<FakeConfig>() {

    override fun capabilityChecker(parentInjector: Injector, config: FakeConfig): BackendCapabilityChecker {
        return BackendCapabilityChecker.Unlimited
    }
    override val id = "fake"

    val starts = mutableListOf<String>()
    val directories = mutableListOf<String>()
    val cancellations = mutableListOf<String>()

    override suspend fun verify(
        parentInjector: Injector,
        config: FakeConfig,
        request: BackendVerificationRequest,
        environment: ExecutionEnvironment,
    ): BackendVerificationResult {
        starts += "${config.id}@${now()}"
        directories += fakeRequest().artifactOutputPath.relativize(request.artifactOutputPath).invariantSeparatorsPathString
        val answer = answers[config.id] ?: Answer(Duration.INFINITE, VerificationVerdict.Inconclusive)
        try {
            delay(answer.after)
        } catch (cancellation: CancellationException) {
            cancellations += "${config.id}@${now()}"
            throw cancellation
        }
        return BackendVerificationResult(
            verdict = answer.verdict,
            metadata = fakeMetadata(config.id),
        )
    }
}

private class StagedPortfolio(
    private val stages: PortfolioStages,
    timeSource: TimeSource,
) : VerificationPortfolio(timeSource) {
    override val id = "staged"
    override val displayName = id
    override val description = "staged test portfolio"
    override val executorKeys: Set<ExecutorKey<*>> = emptySet()
    override val legs = stages.tasks.map {
        it.leg
    }

    override fun availability(environment: ExecutionEnvironment): AvailabilityReport {
        val anyAvailable = stages.tasks.any {
            it.leg.isAvailable(environment)
        }
        return if (anyAvailable) {
            AvailabilityReport.Available
        } else {
            AvailabilityReport.Unavailable("Nothing to run.")
        }
    }

    override suspend fun verify(
        parentInjector: Injector,
        request: BackendVerificationRequest,
        gate: ConcurrencyGate,
        environment: ExecutionEnvironment,
        progress: ProgressContext,
    ): BackendVerificationResult {
        error("StagedPortfolio.verify is unused; tests call run directly")
    }

    suspend fun run(
        gate: ConcurrencyGate,
        timeout: Duration,
        progress: ProgressContext = ProgressContext.Silent,
    ): PortfolioOutcome {
        return staged(startCase(mock(), fakeRequest().copy(timeout = timeout), gate, ExecutionEnvironment.Empty, progress), stages)
    }
}

private val UnavailableExecutorKey = ExecutorKey<BackendExecutor>(name = "unavailable") {
    object : BackendExecutor {
        override fun isAvailable(): Boolean {
            return false
        }
    }
}

private fun within(window: Duration): (PortfolioCase) -> Duration {
    return {
        window
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class VerificationPortfolioStagedTest {

    @Test
    fun `when the tasks fit the case's slots the stages collapse into one race of every task, each in its own directory`() = runTest {
        val backend = recording(
            "c" to Answer(50.seconds, VerificationVerdict.Passed),
        )
        val (a, b, c) = tasks(backend, "a", "b", "c")
        val messages = mutableListOf<String>()
        val portfolio = portfolio {
            stage("first", within(10.seconds)) {
                legs(a, b)
            }
            stage("last") {
                legs(c)
            }
        }

        val outcome = portfolio.run(LimitedConcurrencyGate(3), 120.seconds, recordingProgress(messages))

        assertThat(outcome).isInstanceOf(PortfolioOutcome.Decided::class.java)
        assertThat(currentTime).isEqualTo(50_000)
        assertThat(backend.starts).containsExactlyInAnyOrder("a@0s", "b@0s", "c@0s")
        assertThat(backend.directories).containsExactlyInAnyOrder("a", "b", "c")
        assertThat(messages).startsWith("racing 3 backend configurations")
        assertThat(
            outcome.runs.map {
                it.stage
            },
        ).containsOnlyNulls()
    }

    @Test
    fun `a task listed in stages in a row starts once, in the directory of the first, and its trusted answer cuts the stage running beside it`() = runTest {
        val backend = recording(
            "a" to Answer(25.seconds, VerificationVerdict.Passed),
        )
        val (a, b, c, d) = tasks(backend, "a", "b", "c", "d")
        val messages = mutableListOf<String>()
        val portfolio = portfolio {
            stage("first", within(10.seconds)) {
                legs(a, b)
            }
            stage("second", within(10.seconds)) {
                legs(a, c)
            }
            stage("last") {
                legs(a, d)
            }
        }

        val outcome = portfolio.run(LimitedConcurrencyGate(3), 120.seconds, recordingProgress(messages))

        assertThat(outcome.orFail().metadata.backendId).isEqualTo("a")
        assertThat(currentTime).isEqualTo(25_000)
        assertThat(backend.starts).containsExactlyInAnyOrder("a@0s", "b@0s", "c@10s", "d@20s")
        assertThat(backend.directories).containsExactlyInAnyOrder("first/a", "first/b", "second/c", "last/d")
        assertThat(backend.cancellations).containsExactlyInAnyOrder("b@10s", "c@20s", "d@25s")
        assertThat(messages).contains(
            "running from the first stage: racing 1 backend configurations until the case's time runs out",
            "first stage: racing 1 backend configurations within 10s",
            "second stage: racing 1 backend configurations within 10s",
            "last stage: racing 1 backend configurations until the case's time runs out",
        )
    }

    @Test
    fun `a trusted answer in a stage cuts the tasks running beside the stages`() = runTest {
        val backend = recording(
            "c" to Answer(5.seconds, VerificationVerdict.Passed),
        )
        val (a, b, c) = tasks(backend, "a", "b", "c")
        val portfolio = portfolio {
            stage("first", within(10.seconds)) {
                legs(a, b)
            }
            stage("last") {
                legs(a, c)
            }
        }

        val outcome = portfolio.run(LimitedConcurrencyGate(2), 120.seconds)

        assertThat(outcome.orFail().metadata.backendId).isEqualTo("c")
        assertThat(currentTime).isEqualTo(15_000)
        assertThat(backend.cancellations).containsExactlyInAnyOrder("b@10s", "a@15s")
    }

    @Test
    fun `a task that ended on its own is not run again and one that was cut runs again in the later stage's directory`() = runTest {
        val backend = recording(
            "a" to Answer(1.seconds, VerificationVerdict.Inconclusive),
        )
        val (a, b, c) = tasks(backend, "a", "b", "c")
        val portfolio = portfolio {
            stage("first", within(10.seconds)) {
                legs(a, b)
            }
            stage("second", within(10.seconds)) {
                legs(c)
            }
            stage("last") {
                legs(a, b)
            }
        }

        portfolio.run(LimitedConcurrencyGate(2), 60.seconds)

        assertThat(backend.starts).containsExactlyInAnyOrder("a@0s", "b@0s", "c@10s", "b@20s")
        assertThat(backend.directories).containsExactlyInAnyOrder("first/a", "first/b", "second/c", "last/b")
    }

    @Test
    fun `a stage whose window is shorter than its skippedUnder is skipped`() = runTest {
        val backend = recording()
        val (a, b, c, d) = tasks(backend, "a", "b", "c", "d")
        val portfolio = portfolio {
            stage("first", within(10.seconds)) {
                legs(a, b)
            }
            stage("second", within(5.seconds), skippedUnder = 10.seconds) {
                legs(c)
            }
            stage("last") {
                legs(d)
            }
        }

        portfolio.run(LimitedConcurrencyGate(2), 60.seconds)

        assertThat(backend.starts).containsExactlyInAnyOrder("a@0s", "b@0s", "d@10s")
    }

    @Test
    fun `the tasks under unlessEndedOnItsOwn stay out when that task ended on its own`() = runTest {
        val backend = recording(
            "a" to Answer(1.seconds, VerificationVerdict.Inconclusive),
        )
        val (a, b, c, d, e) = tasks(backend, "a", "b", "c", "d", "e")
        val portfolio = conditionalPortfolio(a, b, c, d, e)

        portfolio.run(LimitedConcurrencyGate(3), 60.seconds)

        assertThat(backend.starts).containsExactlyInAnyOrder("a@0s", "b@0s", "c@10s")
    }

    @Test
    fun `the tasks under unlessEndedOnItsOwn start when that task was cut`() = runTest {
        val backend = recording()
        val (a, b, c, d, e) = tasks(backend, "a", "b", "c", "d", "e")
        val portfolio = conditionalPortfolio(a, b, c, d, e)

        portfolio.run(LimitedConcurrencyGate(3), 60.seconds)

        assertThat(backend.starts).containsExactlyInAnyOrder("a@0s", "b@0s", "c@10s", "d@10s", "e@10s")
    }

    @Test
    fun `the undecided outcome carries every run under the gate it ran in`() = runTest {
        val backend = recording()
        val (a, b, c, d) = tasks(backend, "a", "b", "c", "d")
        val portfolio = portfolio {
            stage("first", within(10.seconds)) {
                legs(a, b)
            }
            stage("second", within(10.seconds)) {
                legs(a, c)
            }
            stage("last") {
                legs(d)
            }
        }

        val outcome = portfolio.run(LimitedConcurrencyGate(3), 60.seconds)

        assertThat(outcome).isInstanceOf(PortfolioOutcome.TimedOut::class.java)
        assertThat(currentTime).isEqualTo(60_000)
        assertThat(
            outcome.runs.map {
                "${it.stage}: ${it.leg} ${it.end}"
            },
        ).containsExactlyInAnyOrder(
            "first stage: fake:b Cut",
            "running from the first stage: fake:a Cut",
            "second stage: fake:c Cut",
            "last stage: fake:d Cut",
        )
    }

    @Test
    fun `a staged case cancelled from outside cancels its tasks and frees their permits`() = runTest {
        val backend = recording()
        val (a, b, c) = tasks(backend, "a", "b", "c")
        val gate = LimitedConcurrencyGate(2)
        val portfolio = portfolio {
            stage("first", within(10.seconds)) {
                legs(a, b)
            }
            stage("last") {
                legs(c)
            }
        }

        val verification = launch {
            portfolio.run(gate, 120.seconds)
        }
        advanceTimeBy(5_000)
        verification.cancelAndJoin()

        assertThat(verification.isCancelled).isTrue
        assertThat(backend.starts).containsExactlyInAnyOrder("a@0s", "b@0s")
        assertThat(backend.cancellations).containsExactlyInAnyOrder("a@5s", "b@5s")
        withTimeout(1_000) {
            coroutineScope {
                repeat(2) {
                    launch {
                        gate.withPermit {
                            delay(10)
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `with no task available the outcome names why and nothing runs`() = runTest {
        val backend = recording(executorKey = UnavailableExecutorKey)
        val (a, b) = tasks(backend, "a", "b")
        val portfolio = portfolio {
            stage("first", within(10.seconds)) {
                legs(a)
            }
            stage("last") {
                legs(b)
            }
        }

        val outcome = portfolio.run(LimitedConcurrencyGate(1), 60.seconds)

        assertThat(outcome).isEqualTo(PortfolioOutcome.NoDecision("Nothing to run.", emptyList()))
        assertThat(backend.starts).isEmpty()
    }

    private fun TestScope.conditionalPortfolio(
        a: PortfolioTask<*>,
        b: PortfolioTask<*>,
        c: PortfolioTask<*>,
        d: PortfolioTask<*>,
        e: PortfolioTask<*>,
    ): StagedPortfolio {
        return portfolio {
            stage("first", within(10.seconds)) {
                legs(a, b)
            }
            stage("last") {
                legs(c)
                unlessEndedOnItsOwn(a) {
                    legs(d, e)
                }
            }
        }
    }

    private fun TestScope.recording(
        vararg answers: Pair<String, Answer>,
        executorKey: ExecutorKey<BackendExecutor> = FakeExecutorKey,
    ): RecordingBackend {
        return RecordingBackend(
            {
                testScheduler.currentTime.milliseconds
            },
            answers.toMap(),
            executorKey,
        )
    }

    private fun tasks(backend: RecordingBackend, vararg names: String): List<PortfolioTask<*>> {
        return names.map {
            PortfolioTask(backend, FakeConfig(it), it)
        }
    }

    private fun TestScope.portfolio(block: StagesScope.() -> Unit): StagedPortfolio {
        return StagedPortfolio(PortfolioStages(block), testScheduler.timeSource)
    }

    private fun recordingProgress(messages: MutableList<String>): ProgressContext {
        return object : ProgressContext {
            override fun checkIsCancelled() {}

            override fun reportProgress(message: String) {
                messages += message
            }
        }
    }

    private fun PortfolioOutcome.orFail(): BackendVerificationResult {
        return (this as PortfolioOutcome.Decided).result
    }
}
