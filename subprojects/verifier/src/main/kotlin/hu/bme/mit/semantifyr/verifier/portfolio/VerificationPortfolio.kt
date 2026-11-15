/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.verifier.portfolio

import com.google.inject.Injector
import hu.bme.mit.semantifyr.backend.BackendMetrics
import hu.bme.mit.semantifyr.backend.BackendVerificationRequest
import hu.bme.mit.semantifyr.backend.BackendVerificationResult
import hu.bme.mit.semantifyr.backend.SoundnessDirection
import hu.bme.mit.semantifyr.backend.VerificationMetadata
import hu.bme.mit.semantifyr.backend.VerificationVerdict
import hu.bme.mit.semantifyr.backend.execution.AvailabilityReport
import hu.bme.mit.semantifyr.backend.execution.ExecutionEnvironment
import hu.bme.mit.semantifyr.backend.execution.ExecutorKey
import hu.bme.mit.semantifyr.logging.info
import hu.bme.mit.semantifyr.logging.loggerFactory
import hu.bme.mit.semantifyr.logging.warn
import hu.bme.mit.semantifyr.verifier.ProgressContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration
import kotlin.time.Instant
import kotlin.time.TimeMark
import kotlin.time.TimeSource

class PortfolioCase internal constructor(
    val request: BackendVerificationRequest,
    val gate: ConcurrencyGate,
    val progress: ProgressContext,
    private val start: TimeMark,
) {
    val time: Duration = request.timeout

    val slots: Int = gate.slots

    fun elapsed(): Duration {
        return start.elapsedNow()
    }

    fun timeLeft(): Duration {
        return (time - elapsed()).coerceAtLeast(Duration.ZERO)
    }
}

class PortfolioScope internal constructor(
    private val coroutineScope: CoroutineScope,
    private val gate: ConcurrencyGate,
) {
    internal val legs = mutableListOf<LaunchedLeg>()

    fun leg(name: String, block: suspend () -> BackendVerificationResult) {
        val started = AtomicBoolean(false)
        val job = coroutineScope.async {
            gate.withPermit {
                started.set(true)
                block()
            }
        }
        legs += LaunchedLeg(name, job, started)
    }
}

internal class LaunchedLeg(
    val name: String,
    val job: Deferred<BackendVerificationResult>,
    val started: AtomicBoolean,
)

sealed interface LegEnd {
    val endedOnItsOwn: Boolean

    data class Finished(val result: BackendVerificationResult) : LegEnd {
        override val endedOnItsOwn = true
    }

    data class Threw(val failure: Throwable) : LegEnd {
        override val endedOnItsOwn = true
    }

    data object Cut : LegEnd {
        override val endedOnItsOwn = false
    }

    data object NeverStarted : LegEnd {
        override val endedOnItsOwn = false
    }
}

data class LegRun(
    val leg: String,
    val stage: String?,
    val end: LegEnd,
)

data class PortfolioLeg(
    val id: String,
    val soundness: SoundnessDirection,
)

fun BackendVerificationRequest.withSubPath(subpath: String): BackendVerificationRequest {
    return this.copy(artifactOutputPath = artifactOutputPath.resolve(subpath))
}

sealed interface PortfolioOutcome {
    val runs: List<LegRun>

    data class Decided(
        val result: BackendVerificationResult,
        override val runs: List<LegRun> = emptyList(),
    ) : PortfolioOutcome {
        override fun toBackendVerificationResult(startedAt: Instant): BackendVerificationResult {
            return result.copy(
                metadata = result.metadata.copy(startedAt = startedAt),
            )
        }
    }

    data class AllErrored(
        val failures: List<String>,
        override val runs: List<LegRun> = emptyList(),
    ) : PortfolioOutcome {
        override fun toBackendVerificationResult(startedAt: Instant): BackendVerificationResult {
            val firstMessage = failures.firstOrNull() ?: "(no message)"
            val trailingCount = (failures.size - 1).coerceAtLeast(0)
            val summary = if (trailingCount > 0) {
                "$firstMessage (+$trailingCount more)"
            } else {
                firstMessage
            }
            return undecidedResult(startedAt, VerificationVerdict.Errored, "All portfolio jobs errored: $summary")
        }
    }

    data class NoDecision(
        val reason: String,
        override val runs: List<LegRun> = emptyList(),
    ) : PortfolioOutcome {
        override fun toBackendVerificationResult(startedAt: Instant): BackendVerificationResult {
            return undecidedResult(startedAt, VerificationVerdict.Inconclusive, reason)
        }
    }

    data class TimedOut(
        val reason: String,
        override val runs: List<LegRun> = emptyList(),
    ) : PortfolioOutcome {
        override fun toBackendVerificationResult(startedAt: Instant): BackendVerificationResult {
            return undecidedResult(startedAt, VerificationVerdict.Timeout, reason)
        }
    }

    data class AllNotSupported(
        val reason: String,
        override val runs: List<LegRun> = emptyList(),
    ) : PortfolioOutcome {
        override fun toBackendVerificationResult(startedAt: Instant): BackendVerificationResult {
            return undecidedResult(startedAt, VerificationVerdict.NotSupported, reason)
        }
    }

    fun toBackendVerificationResult(startedAt: Instant): BackendVerificationResult
}

private fun undecidedResult(
    startedAt: Instant,
    verdict: VerificationVerdict,
    message: String,
): BackendVerificationResult {
    return BackendVerificationResult(
        metadata = VerificationMetadata(
            backendId = null,
            startedAt = startedAt,
        ),
        verdict = verdict,
        metrics = BackendMetrics(),
        message = message,
    )
}

private class GateRace(
    val decided: BackendVerificationResult?,
    val runs: List<LegRun>,
)

private class IndexedEnd(
    val index: Int,
    val end: LegEnd,
)

abstract class VerificationPortfolio(
    private val timeSource: TimeSource = TimeSource.Monotonic,
) {

    protected val logger by loggerFactory()

    abstract val id: String
    abstract val displayName: String
    abstract val description: String
    abstract val executorKeys: Set<ExecutorKey<*>>
    abstract val legs: List<PortfolioLeg>

    abstract fun availability(environment: ExecutionEnvironment = ExecutionEnvironment.Empty): AvailabilityReport

    abstract suspend fun verify(
        parentInjector: Injector,
        request: BackendVerificationRequest,
        gate: ConcurrencyGate,
        environment: ExecutionEnvironment,
        progress: ProgressContext,
    ): BackendVerificationResult

    protected fun startCase(
        request: BackendVerificationRequest,
        gate: ConcurrencyGate,
        progress: ProgressContext,
    ): PortfolioCase {
        return PortfolioCase(request, gate, progress, timeSource.markNow())
    }

    protected suspend fun firstDecisive(
        case: PortfolioCase,
        window: Duration = Duration.INFINITE,
        stage: String? = null,
        block: PortfolioScope.() -> Unit,
    ): PortfolioOutcome = supervisorScope {
        val scope = PortfolioScope(this, case.gate)
        scope.block()

        require(scope.legs.isNotEmpty()) { "$id: firstDecisive requires at least one job" }
        logger.info { "$id: firstDecisive over ${scope.legs.size} job(s)${stageSuffix(stage)}, window=${describedBound(case, window)}" }

        val race = race(case, window, stage, scope) {
            if (it.isTrusted) {
                logger.info { "$id: job produced trusted ${it.verdict}" }
                it
            } else {
                null
            }
        }

        cancelRemainingJobs()
        if (race.decided != null) {
            PortfolioOutcome.Decided(race.decided, race.runs)
        } else {
            classify(race.runs, describedBound(case, window))
        }
    }

    protected suspend fun firstNDecisive(
        count: Int,
        case: PortfolioCase,
        window: Duration = Duration.INFINITE,
        stage: String? = null,
        block: PortfolioScope.() -> Unit,
    ): PortfolioOutcome = supervisorScope {
        val scope = PortfolioScope(this, case.gate)
        scope.block()

        require(scope.legs.isNotEmpty()) { "$id: firstNDecisive requires at least one job" }
        logger.info { "$id: firstNDecisive(n=$count) over ${scope.legs.size} job(s)${stageSuffix(stage)}, window=${describedBound(case, window)}" }

        val decisive = mutableListOf<BackendVerificationResult>()
        val race = race(case, window, stage, scope) { result ->
            if (!result.isTrusted) {
                return@race null
            }
            decisive += result
            if (decisive.size < count) {
                return@race null
            }
            val allAgree = decisive.map { it.verdict }.distinct().size == 1
            if (allAgree) {
                decisive.first()
            } else {
                val breakdown = decisive.joinToString(", ") { "${it.metadata.backendId}=${it.verdict}" }
                logger.warn { "$id: firstNDecisive(n=$count) disagreement among the first $count decisive results ($breakdown)" }
                null
            }
        }

        cancelRemainingJobs()

        when {
            race.decided != null -> PortfolioOutcome.Decided(race.decided, race.runs)
            decisive.size >= count -> {
                val breakdown = decisive.joinToString(", ") { "${it.metadata.backendId}=${it.verdict}" }
                PortfolioOutcome.NoDecision("$id: firstNDecisive(n=$count) disagreement ($breakdown)", race.runs)
            }
            else -> classify(race.runs, describedBound(case, window))
        }
    }

    protected suspend fun all(
        case: PortfolioCase,
        window: Duration = Duration.INFINITE,
        stage: String? = null,
        block: PortfolioScope.() -> Unit,
    ): PortfolioOutcome = supervisorScope {
        val scope = PortfolioScope(this, case.gate)
        scope.block()

        require(scope.legs.isNotEmpty()) { "$id: all requires at least one job" }
        logger.info { "$id: all over ${scope.legs.size} job(s)${stageSuffix(stage)}, window=${describedBound(case, window)}" }

        val race = race(case, window, stage, scope) {
            null
        }

        cancelRemainingJobs()

        val collected = race.runs.mapNotNull {
            (it.end as? LegEnd.Finished)?.result
        }
        val decisive = collected.filter { it.isDecisive }
        val trusted = decisive.filter { it.isTrusted }
        when {
            trusted.isEmpty() -> classify(race.runs, describedBound(case, window))
            trusted.map { it.verdict }.distinct().size > 1 -> {
                val breakdown = trusted.joinToString(", ") { "${it.metadata.backendId}=${it.verdict} (${it.soundness})" }
                logger.warn { "$id: trusted verdicts disagree ($breakdown), one of these legs is unsound" }
                PortfolioOutcome.Decided(
                    BackendVerificationResult(
                        verdict = VerificationVerdict.Errored,
                        metadata = VerificationMetadata(
                            backendId = null,
                            startedAt = trusted.first().metadata.startedAt,
                        ),
                        message = "Portfolio '$id' saw trusted verdicts disagree: $breakdown",
                    ),
                    race.runs,
                )
            }
            else -> {
                val agreed = trusted.first()
                val overruled = decisive.filter { it.verdict != agreed.verdict }
                if (overruled.isEmpty()) {
                    PortfolioOutcome.Decided(agreed, race.runs)
                } else {
                    val breakdown = overruled.joinToString(", ") { "${it.metadata.backendId}=${it.verdict} (not trusted, ${it.soundness})" }
                    logger.info { "$id: resolved to ${agreed.verdict} over $breakdown" }
                    PortfolioOutcome.Decided(agreed.copy(message = "Portfolio '$id' resolved to ${agreed.verdict} from ${agreed.metadata.backendId} over $breakdown"), race.runs)
                }
            }
        }
    }

    protected fun undecided(runs: List<LegRun>): PortfolioOutcome {
        return classify(runs, null)
    }

    private suspend fun CoroutineScope.race(
        case: PortfolioCase,
        window: Duration,
        stage: String?,
        scope: PortfolioScope,
        onResult: (BackendVerificationResult) -> BackendVerificationResult?,
    ): GateRace {
        val results = funnelLegsIntoChannel(scope)
        val total = scope.legs.size
        val ends = arrayOfNulls<LegEnd>(total)
        val progressPrefix = if (stage == null) {
            ""
        } else {
            "$stage stage: "
        }
        var done = 0
        val decided = withTimeoutOrNull(minOf(window, case.timeLeft())) {
            while (done < total) {
                val received = results.receive()
                done++
                ends[received.index] = received.end
                val end = received.end
                if (end is LegEnd.Finished) {
                    case.progress.reportProgress("${progressPrefix}result $done/$total: ${end.result.verdict}")
                    val finalResult = onResult(end.result)
                    if (finalResult != null) {
                        return@withTimeoutOrNull finalResult
                    }
                }
            }
            null
        }
        val runs = scope.legs.mapIndexed { index, leg ->
            val end = ends[index] ?: if (leg.started.get()) {
                LegEnd.Cut
            } else {
                LegEnd.NeverStarted
            }
            LegRun(leg.name, stage, end)
        }
        return GateRace(decided, runs)
    }

    private fun classify(runs: List<LegRun>, bound: Duration?): PortfolioOutcome {
        if (runs.isEmpty()) {
            return PortfolioOutcome.NoDecision("$id: no leg ran", runs)
        }
        val lastRuns = runs.groupBy {
            it.leg
        }.values.map {
            it.last()
        }
        val lastEnds = lastRuns.map {
            it.end
        }
        val results = lastEnds.mapNotNull {
            (it as? LegEnd.Finished)?.result
        }
        val observed = describeObserved(runs)
        val timedOutLegs = lastRuns.filter {
            val end = it.end
            end is LegEnd.Finished && end.result.verdict == VerificationVerdict.Timeout
        }
        return when {
            lastEnds.any { !it.endedOnItsOwn } -> {
                val reason = if (bound == null) {
                    "$id: no trusted verdict$observed"
                } else {
                    "$id: no trusted verdict within $bound$observed"
                }
                PortfolioOutcome.TimedOut(reason, runs)
            }
            results.size == lastEnds.size && results.all { it.verdict == VerificationVerdict.NotSupported } -> allNotSupported(results, runs)
            lastEnds.all { it is LegEnd.Threw || (it is LegEnd.Finished && it.result.verdict == VerificationVerdict.Errored) } -> {
                val failures = lastEnds.mapNotNull {
                    (it as? LegEnd.Threw)?.failure
                }.map {
                    "${it::class.simpleName}: ${it.message ?: ""}"
                } + results.map {
                    "${it.metadata.backendId}: ${it.message ?: ""}"
                }
                PortfolioOutcome.AllErrored(failures, runs)
            }
            timedOutLegs.isNotEmpty() -> {
                val legs = timedOutLegs.joinToString(", ") {
                    it.leg
                }
                PortfolioOutcome.TimedOut("$id: no trusted verdict, timed out: $legs$observed", runs)
            }
            else -> PortfolioOutcome.NoDecision("$id: no trusted verdict$observed", runs)
        }
    }

    private fun allNotSupported(collected: List<BackendVerificationResult>, runs: List<LegRun>): PortfolioOutcome.AllNotSupported {
        val legs = collected.map {
            "${it.metadata.backendId ?: "?"} (${it.message ?: "no construct reported"})"
        }.distinct().joinToString(", ")
        return PortfolioOutcome.AllNotSupported("$id: every backend reported NotSupported: $legs", runs)
    }

    private fun describeObserved(runs: List<LegRun>): String {
        val stages = runs.groupBy {
            it.stage
        }
        if (stages.keys == setOf(null)) {
            return ", observed ${describeRuns(runs)}"
        }
        val described = stages.entries.joinToString("; ") { (stage, stageRuns) ->
            "$stage stage: ${describeRuns(stageRuns)}"
        }
        return ", observed $described"
    }

    private fun describeRuns(runs: List<LegRun>): String {
        return runs.joinToString(", ") {
            describeRun(it)
        }
    }

    private fun describeRun(run: LegRun): String {
        return when (val end = run.end) {
            is LegEnd.Finished -> {
                val result = end.result
                if (result.isDecisive && !result.isTrusted) {
                    "${run.leg}=${result.verdict} (not trusted, ${result.soundness})"
                } else {
                    "${run.leg}=${result.verdict}"
                }
            }
            is LegEnd.Threw -> "${run.leg} threw ${end.failure::class.simpleName}"
            LegEnd.Cut -> "${run.leg} cut"
            LegEnd.NeverStarted -> "${run.leg} never started"
        }
    }

    private fun describedBound(case: PortfolioCase, window: Duration): Duration {
        return minOf(window, case.time)
    }

    private fun stageSuffix(stage: String?): String {
        return if (stage == null) {
            ""
        } else {
            " in the $stage stage"
        }
    }

    private fun CoroutineScope.funnelLegsIntoChannel(scope: PortfolioScope): Channel<IndexedEnd> {
        val results = Channel<IndexedEnd>(Channel.UNLIMITED)
        scope.legs.forEachIndexed { index, leg ->
            launch {
                val end = try {
                    LegEnd.Finished(leg.job.await())
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (throwable: Throwable) {
                    logger.warn { "$id: job threw ${throwable::class.simpleName}: ${throwable.message ?: ""}" }
                    LegEnd.Threw(throwable)
                }

                results.send(IndexedEnd(index, end))
            }
        }
        return results
    }

    private fun CoroutineScope.cancelRemainingJobs() {
        coroutineContext.cancelChildren()
    }
}
