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
import hu.bme.mit.semantifyr.verifier.noReadBack
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
        readBack: suspend (BackendVerificationResult) -> BackendVerificationResult,
    ): BackendVerificationResult {
        error("StagedPortfolio.verify is unused; tests call run directly")
    }

    suspend fun run(
        gate: ConcurrencyGate,
        timeout: Duration,
        progress: ProgressContext = ProgressContext.Silent,
    ): PortfolioOutcome {
        return staged(startCase(mock(), fakeRequest().copy(timeout = timeout), gate, ExecutionEnvironment.Empty, progress, noReadBack), stages)
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
            "gamma" to Answer(50.seconds, VerificationVerdict.Passed),
        )
        val (alpha, beta, gamma) = tasks(backend, "alpha", "beta", "gamma")
        val messages = mutableListOf<String>()
        val portfolio = portfolio {
            stage("first", within(10.seconds)) {
                legs(alpha, beta)
            }
            stage("last") {
                legs(gamma)
            }
        }

        val outcome = portfolio.run(LimitedConcurrencyGate(3), 120.seconds, recordingProgress(messages))

        assertThat(outcome).isInstanceOf(PortfolioOutcome.Decided::class.java)
        assertThat(currentTime).isEqualTo(50_000)
        assertThat(backend.starts).containsExactlyInAnyOrder("alpha@0s", "beta@0s", "gamma@0s")
        assertThat(backend.directories).containsExactlyInAnyOrder("alpha", "beta", "gamma")
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
            "alpha" to Answer(25.seconds, VerificationVerdict.Passed),
        )
        val (alpha, beta, gamma, delta) = tasks(backend, "alpha", "beta", "gamma", "delta")
        val messages = mutableListOf<String>()
        val portfolio = portfolio {
            stage("first", within(10.seconds)) {
                legs(alpha, beta)
            }
            stage("second", within(10.seconds)) {
                legs(alpha, gamma)
            }
            stage("last") {
                legs(alpha, delta)
            }
        }

        val outcome = portfolio.run(LimitedConcurrencyGate(3), 120.seconds, recordingProgress(messages))

        assertThat(outcome.orFail().metadata.backendId).isEqualTo("alpha")
        assertThat(currentTime).isEqualTo(25_000)
        assertThat(backend.starts).containsExactlyInAnyOrder("alpha@0s", "beta@0s", "gamma@10s", "delta@20s")
        assertThat(backend.directories).containsExactlyInAnyOrder("first/alpha", "first/beta", "second/gamma", "last/delta")
        assertThat(backend.cancellations).containsExactlyInAnyOrder("beta@10s", "gamma@20s", "delta@25s")
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
            "gamma" to Answer(5.seconds, VerificationVerdict.Passed),
        )
        val (alpha, beta, gamma) = tasks(backend, "alpha", "beta", "gamma")
        val portfolio = portfolio {
            stage("first", within(10.seconds)) {
                legs(alpha, beta)
            }
            stage("last") {
                legs(alpha, gamma)
            }
        }

        val outcome = portfolio.run(LimitedConcurrencyGate(2), 120.seconds)

        assertThat(outcome.orFail().metadata.backendId).isEqualTo("gamma")
        assertThat(currentTime).isEqualTo(15_000)
        assertThat(backend.cancellations).containsExactlyInAnyOrder("beta@10s", "alpha@15s")
    }

    @Test
    fun `a task that ended on its own is not run again and one that was cut runs again in the later stage's directory`() = runTest {
        val backend = recording(
            "alpha" to Answer(1.seconds, VerificationVerdict.Inconclusive),
        )
        val (alpha, beta, gamma) = tasks(backend, "alpha", "beta", "gamma")
        val portfolio = portfolio {
            stage("first", within(10.seconds)) {
                legs(alpha, beta)
            }
            stage("second", within(10.seconds)) {
                legs(gamma)
            }
            stage("last") {
                legs(alpha, beta)
            }
        }

        portfolio.run(LimitedConcurrencyGate(2), 60.seconds)

        assertThat(backend.starts).containsExactlyInAnyOrder("alpha@0s", "beta@0s", "gamma@10s", "beta@20s")
        assertThat(backend.directories).containsExactlyInAnyOrder("first/alpha", "first/beta", "second/gamma", "last/beta")
    }

    @Test
    fun `a stage whose window is shorter than its skippedUnder is skipped`() = runTest {
        val backend = recording()
        val (alpha, beta, gamma, delta) = tasks(backend, "alpha", "beta", "gamma", "delta")
        val portfolio = portfolio {
            stage("first", within(10.seconds)) {
                legs(alpha, beta)
            }
            stage("second", within(5.seconds), skippedUnder = 10.seconds) {
                legs(gamma)
            }
            stage("last") {
                legs(delta)
            }
        }

        portfolio.run(LimitedConcurrencyGate(2), 60.seconds)

        assertThat(backend.starts).containsExactlyInAnyOrder("alpha@0s", "beta@0s", "delta@10s")
    }

    @Test
    fun `the tasks under unlessEndedOnItsOwn stay out when that task ended on its own`() = runTest {
        val backend = recording(
            "alpha" to Answer(1.seconds, VerificationVerdict.Inconclusive),
        )
        val (alpha, beta, gamma, delta, epsilon) = tasks(backend, "alpha", "beta", "gamma", "delta", "epsilon")
        val portfolio = conditionalPortfolio(alpha, beta, gamma, delta, epsilon)

        portfolio.run(LimitedConcurrencyGate(3), 60.seconds)

        assertThat(backend.starts).containsExactlyInAnyOrder("alpha@0s", "beta@0s", "gamma@10s")
    }

    @Test
    fun `the tasks under unlessEndedOnItsOwn start when that task was cut`() = runTest {
        val backend = recording()
        val (alpha, beta, gamma, delta, epsilon) = tasks(backend, "alpha", "beta", "gamma", "delta", "epsilon")
        val portfolio = conditionalPortfolio(alpha, beta, gamma, delta, epsilon)

        portfolio.run(LimitedConcurrencyGate(3), 60.seconds)

        assertThat(backend.starts).containsExactlyInAnyOrder("alpha@0s", "beta@0s", "gamma@10s", "delta@10s", "epsilon@10s")
    }

    @Test
    fun `the undecided outcome carries every run under the gate it ran in`() = runTest {
        val backend = recording()
        val (alpha, beta, gamma, delta) = tasks(backend, "alpha", "beta", "gamma", "delta")
        val portfolio = portfolio {
            stage("first", within(10.seconds)) {
                legs(alpha, beta)
            }
            stage("second", within(10.seconds)) {
                legs(alpha, gamma)
            }
            stage("last") {
                legs(delta)
            }
        }

        val outcome = portfolio.run(LimitedConcurrencyGate(3), 60.seconds)

        assertThat(outcome).isInstanceOf(PortfolioOutcome.TimedOut::class.java)
        assertThat(currentTime).isEqualTo(60_000)
        assertThat(
            outcome.runs.map {
                "${it.stage}: ${it.legId} ${it.end}"
            },
        ).containsExactlyInAnyOrder(
            "first stage: fake:beta Cut",
            "running from the first stage: fake:alpha Cut",
            "second stage: fake:gamma Cut",
            "last stage: fake:delta Cut",
        )
    }

    @Test
    fun `a staged case cancelled from outside cancels its tasks and frees their permits`() = runTest {
        val backend = recording()
        val (alpha, beta, gamma) = tasks(backend, "alpha", "beta", "gamma")
        val gate = LimitedConcurrencyGate(2)
        val portfolio = portfolio {
            stage("first", within(10.seconds)) {
                legs(alpha, beta)
            }
            stage("last") {
                legs(gamma)
            }
        }

        val verification = launch {
            portfolio.run(gate, 120.seconds)
        }
        advanceTimeBy(5_000)
        verification.cancelAndJoin()

        assertThat(verification.isCancelled).isTrue
        assertThat(backend.starts).containsExactlyInAnyOrder("alpha@0s", "beta@0s")
        assertThat(backend.cancellations).containsExactlyInAnyOrder("alpha@5s", "beta@5s")
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
        val (alpha, beta) = tasks(backend, "alpha", "beta")
        val portfolio = portfolio {
            stage("first", within(10.seconds)) {
                legs(alpha)
            }
            stage("last") {
                legs(beta)
            }
        }

        val outcome = portfolio.run(LimitedConcurrencyGate(1), 60.seconds)

        assertThat(outcome).isEqualTo(PortfolioOutcome.NoDecision("Nothing to run.", emptyList()))
        assertThat(backend.starts).isEmpty()
    }

    private fun TestScope.conditionalPortfolio(
        alpha: PortfolioTask<*>,
        beta: PortfolioTask<*>,
        gamma: PortfolioTask<*>,
        delta: PortfolioTask<*>,
        epsilon: PortfolioTask<*>,
    ): StagedPortfolio {
        return portfolio {
            stage("first", within(10.seconds)) {
                legs(alpha, beta)
            }
            stage("last") {
                legs(gamma)
                unlessEndedOnItsOwn(alpha) {
                    legs(delta, epsilon)
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
