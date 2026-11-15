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
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicReferenceArray
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

class PortfolioScope internal constructor() {
    internal val legs = mutableListOf<LegSpecification>()

    fun leg(name: String, block: suspend () -> BackendVerificationResult) {
        legs += LegSpecification(name, block)
    }
}

internal class LegSpecification(
    val name: String,
    val block: suspend () -> BackendVerificationResult,
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
        override val runs: List<LegRun>,
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
        override val runs: List<LegRun>,
    ) : PortfolioOutcome {
        override fun toBackendVerificationResult(startedAt: Instant): BackendVerificationResult {
            return undecidedResult(startedAt, VerificationVerdict.Inconclusive, reason)
        }
    }

    data class TimedOut(
        val reason: String,
        override val runs: List<LegRun>,
    ) : PortfolioOutcome {
        override fun toBackendVerificationResult(startedAt: Instant): BackendVerificationResult {
            return undecidedResult(startedAt, VerificationVerdict.Timeout, reason)
        }
    }

    data class AllNotSupported(
        val reason: String,
        override val runs: List<LegRun>,
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

private class LegResult(
    val leg: String,
    val result: BackendVerificationResult,
)

private enum class LegState {
    Waiting,
    Started,
    Closed,
}

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
    ): PortfolioOutcome {
        val scope = PortfolioScope()
        scope.block()

        require(scope.legs.isNotEmpty()) { "$id: firstDecisive requires at least one job" }
        logger.info { "$id: firstDecisive over ${scope.legs.size} job(s)${stageSuffix(stage)}, window=${describedBound(case, window)}" }

        val race = race(case, window, stage, scope) {
            if (it.result.isTrusted) {
                logger.info { "$id: ${it.leg} produced trusted ${it.result.verdict}" }
                it.result
            } else {
                null
            }
        }

        return if (race.decided != null) {
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
    ): PortfolioOutcome {
        val scope = PortfolioScope()
        scope.block()

        require(scope.legs.isNotEmpty()) { "$id: firstNDecisive requires at least one job" }
        logger.info { "$id: firstNDecisive(n=$count) over ${scope.legs.size} job(s)${stageSuffix(stage)}, window=${describedBound(case, window)}" }

        val decisive = mutableListOf<LegResult>()
        val race = race(case, window, stage, scope) {
            if (!it.result.isTrusted) {
                return@race null
            }
            decisive += it
            if (decisive.size < count) {
                return@race null
            }
            val allAgree = decisive.map { decided ->
                decided.result.verdict
            }.distinct().size == 1
            if (allAgree) {
                decisive.first().result
            } else {
                logger.warn { "$id: firstNDecisive(n=$count) disagreement among the first $count decisive results (${describeVerdicts(decisive)})" }
                null
            }
        }

        return when {
            race.decided != null -> PortfolioOutcome.Decided(race.decided, race.runs)
            decisive.size >= count -> PortfolioOutcome.NoDecision("$id: firstNDecisive(n=$count) disagreement (${describeVerdicts(decisive)})", race.runs)
            else -> classify(race.runs, describedBound(case, window))
        }
    }

    protected suspend fun all(
        case: PortfolioCase,
        window: Duration = Duration.INFINITE,
        stage: String? = null,
        block: PortfolioScope.() -> Unit,
    ): PortfolioOutcome {
        val scope = PortfolioScope()
        scope.block()

        require(scope.legs.isNotEmpty()) { "$id: all requires at least one job" }
        logger.info { "$id: all over ${scope.legs.size} job(s)${stageSuffix(stage)}, window=${describedBound(case, window)}" }

        val race = race(case, window, stage, scope) {
            null
        }

        val decisive = race.runs.mapNotNull {
            val end = it.end
            if (end is LegEnd.Finished && end.result.isDecisive) {
                LegResult(it.leg, end.result)
            } else {
                null
            }
        }
        val trusted = decisive.filter {
            it.result.isTrusted
        }
        return when {
            trusted.isEmpty() -> classify(race.runs, describedBound(case, window))
            trusted.map { it.result.verdict }.distinct().size > 1 -> {
                val breakdown = trusted.joinToString(", ") {
                    "${it.leg}=${it.result.verdict} (${it.result.soundness})"
                }
                logger.warn { "$id: trusted verdicts disagree ($breakdown), one of these legs is unsound" }
                PortfolioOutcome.Decided(
                    BackendVerificationResult(
                        verdict = VerificationVerdict.Errored,
                        metadata = VerificationMetadata(
                            backendId = null,
                            startedAt = trusted.first().result.metadata.startedAt,
                        ),
                        message = "Portfolio '$id' saw trusted verdicts disagree: $breakdown",
                    ),
                    race.runs,
                )
            }
            else -> {
                val agreed = trusted.first()
                val overruled = decisive.filter {
                    it.result.verdict != agreed.result.verdict
                }
                if (overruled.isEmpty()) {
                    PortfolioOutcome.Decided(agreed.result, race.runs)
                } else {
                    val breakdown = overruled.joinToString(", ") {
                        "${it.leg}=${it.result.verdict} (not trusted, ${it.result.soundness})"
                    }
                    logger.info { "$id: resolved to ${agreed.result.verdict} over $breakdown" }
                    PortfolioOutcome.Decided(agreed.result.copy(message = "Portfolio '$id' resolved to ${agreed.result.verdict} from ${agreed.leg} over $breakdown"), race.runs)
                }
            }
        }
    }

    protected fun undecided(runs: List<LegRun>): PortfolioOutcome {
        return classify(runs, null)
    }

    // no leg starts after the close, and each leg's end is read only once every leg has stopped
    private suspend fun race(
        case: PortfolioCase,
        window: Duration,
        stage: String?,
        scope: PortfolioScope,
        onResult: (LegResult) -> BackendVerificationResult?,
    ): GateRace = coroutineScope {
        val legs = scope.legs
        val total = legs.size
        val states = List(total) {
            AtomicReference(LegState.Waiting)
        }
        val ends = AtomicReferenceArray<LegEnd>(total)
        val finished = Channel<Int>(Channel.UNLIMITED)
        val progressPrefix = if (stage == null) {
            ""
        } else {
            "$stage: "
        }
        var received = 0
        var decided: BackendVerificationResult? = null

        fun receive(index: Int) {
            received++
            val end = ends.get(index)
            if (end is LegEnd.Finished) {
                case.progress.reportProgress("${progressPrefix}result $received/$total: ${end.result.verdict}")
                decided = onResult(LegResult(legs[index].name, end.result))
            }
        }

        for ((index, leg) in legs.withIndex()) {
            launch {
                case.gate.withPermit {
                    if (states[index].compareAndSet(LegState.Waiting, LegState.Started)) {
                        ends.set(index, runLeg(leg))
                        finished.trySend(index)
                    }
                }
            }
        }

        withTimeoutOrNull(minOf(window, case.timeLeft())) {
            while (received < total && decided == null) {
                receive(finished.receive())
            }
        }

        for (state in states) {
            state.compareAndSet(LegState.Waiting, LegState.Closed)
        }
        coroutineContext.cancelChildren()
        coroutineContext.job.children.toList().joinAll()

        while (decided == null) {
            val index = finished.tryReceive().getOrNull() ?: break
            receive(index)
        }

        val runs = legs.mapIndexed { index, leg ->
            val end = ends.get(index) ?: if (states[index].get() == LegState.Started) {
                LegEnd.Cut
            } else {
                LegEnd.NeverStarted
            }
            LegRun(leg.name, stage, end)
        }
        GateRace(decided, runs)
    }

    private suspend fun runLeg(leg: LegSpecification): LegEnd {
        return try {
            LegEnd.Finished(leg.block())
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            logger.warn { "$id: ${leg.name} threw ${throwable::class.simpleName}: ${throwable.message ?: ""}" }
            LegEnd.Threw(throwable)
        }
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
        val lastResults = lastRuns.mapNotNull {
            (it.end as? LegEnd.Finished)?.let { finished ->
                LegResult(it.leg, finished.result)
            }
        }
        val observed = describeObserved(runs)
        val timedOutLegs = lastResults.filter {
            it.result.verdict == VerificationVerdict.Timeout
        }
        return when {
            lastRuns.any { !it.end.endedOnItsOwn } -> {
                val reason = if (bound == null) {
                    "$id: no trusted verdict$observed"
                } else {
                    "$id: no trusted verdict within $bound$observed"
                }
                PortfolioOutcome.TimedOut(reason, runs)
            }
            lastResults.size == lastRuns.size && lastResults.all { it.result.verdict == VerificationVerdict.NotSupported } -> {
                val legs = lastResults.joinToString(", ") {
                    "${it.leg} (${it.result.message ?: "no construct reported"})"
                }
                PortfolioOutcome.AllNotSupported("$id: every leg reported NotSupported: $legs", runs)
            }
            lastRuns.all { it.end is LegEnd.Threw || (it.end as? LegEnd.Finished)?.result?.verdict == VerificationVerdict.Errored } -> {
                val failures = lastRuns.map {
                    when (val end = it.end) {
                        is LegEnd.Threw -> "${it.leg}: ${end.failure::class.simpleName}: ${end.failure.message ?: ""}"
                        is LegEnd.Finished -> "${it.leg}: ${end.result.message ?: ""}"
                        else -> error("only failed legs reach here")
                    }
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

    private fun describeVerdicts(results: List<LegResult>): String {
        return results.joinToString(", ") {
            "${it.leg}=${it.result.verdict}"
        }
    }

    private fun describeObserved(runs: List<LegRun>): String {
        val stages = runs.groupBy {
            it.stage
        }
        if (stages.keys == setOf(null)) {
            return ", observed ${describeRuns(runs)}"
        }
        val described = stages.entries.joinToString("; ") { (stage, stageRuns) ->
            "$stage: ${describeRuns(stageRuns)}"
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
            " in $stage"
        }
    }
}
