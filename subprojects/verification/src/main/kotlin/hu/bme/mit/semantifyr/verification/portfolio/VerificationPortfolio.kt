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
import hu.bme.mit.semantifyr.backend.VerificationRunMetadata
import hu.bme.mit.semantifyr.backend.VerificationVerdict
import hu.bme.mit.semantifyr.logging.info
import hu.bme.mit.semantifyr.logging.loggerFactory
import hu.bme.mit.semantifyr.logging.warn
import hu.bme.mit.semantifyr.verification.ProgressContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration

class PortfolioScope internal constructor(
    private val coroutineScope: CoroutineScope,
    val executor: BackendExecutor,
) {
    internal val jobs = mutableListOf<Deferred<VerificationResult>>()

    fun async(block: suspend () -> VerificationResult) {
        jobs += coroutineScope.async {
            block()
        }
    }
}

fun VerificationRequest.withSubPath(subpath: String): VerificationRequest {
    return this.copy(artifactOutputPath = artifactOutputPath.resolve(subpath))
}

sealed class PortfolioOutcome {
    data class Decided(val result: VerificationResult) : PortfolioOutcome()
    data class AllErrored(val exceptions: List<Throwable>) : PortfolioOutcome()
    data class NoDecision(val reason: String) : PortfolioOutcome()
}

fun PortfolioOutcome.toVerificationResult(
    metadata: VerificationRunMetadata,
    metrics: VerificationMetrics = VerificationMetrics(),
): VerificationResult = when (this) {
    is PortfolioOutcome.Decided -> result
    is PortfolioOutcome.AllErrored -> {
        val firstMessage = exceptions.firstOrNull()?.let {
            "${it::class.simpleName}: ${it.message ?: ""}"
        } ?: "(no message)"
        val trailingCount = (exceptions.size - 1).coerceAtLeast(0)
        val summary = if (trailingCount > 0) "$firstMessage (+$trailingCount more)" else firstMessage
        VerificationResult.errored(
            metadata = metadata,
            metrics = metrics,
            message = "All portfolio jobs errored: $summary",
        )
    }
    is PortfolioOutcome.NoDecision -> VerificationResult.inconclusive(
        metadata = metadata,
        metrics = metrics,
        message = reason,
    )
}

abstract class VerificationPortfolio {

    val logger by loggerFactory()

    abstract val id: String
    abstract val displayName: String
    abstract val description: String
    abstract val familyId: String

    abstract fun availability(environment: ExecutionEnvironment = ExecutionEnvironment.Empty): AvailabilityReport

    abstract suspend fun verify(
        request: VerificationRequest,
        executor: BackendExecutor,
        environment: ExecutionEnvironment,
        progress: ProgressContext,
    ): VerificationResult

    /**
     * Launch every job registered in [block] in parallel. Returns the first decisive result
     * and cancels every still-running child. When no decisive result arrives in time the
     * outcome distinguishes "all jobs errored" from "some jobs still pending at timeout"
     * so callers can surface the right verdict (Errored vs Inconclusive).
     */
    protected suspend fun firstDecisive(
        executor: BackendExecutor,
        timeout: Duration = Duration.INFINITE,
        progress: ProgressContext = ProgressContext.NoOp,
        block: PortfolioScope.() -> Unit,
    ): PortfolioOutcome = supervisorScope {
        val scope = PortfolioScope(this, executor)
        scope.block()

        require(scope.jobs.isNotEmpty()) { "$id: firstDecisive requires at least one job" }
        logger.info { "$id: firstDecisive over ${scope.jobs.size} job(s), timeout=$timeout" }

        val outcome = receiveResultsWithTimeout(scope, timeout, progress) { result ->
            if (result.isDecisive) {
                logger.info { "$id: job produced decisive ${result.verdict}" }
                result
            } else {
                null
            }
        }

        cancelRemainingJobs()
        outcome
    }

    /**
     * Launch every job registered in [block] in parallel. Waits for [count] decisive results.
     * If they all agree, returns that verdict. On disagreement, fewer-than-[count] decisive
     * results within [timeout], or too many job errors, returns a non-decided outcome that
     * tells the caller *why* (all-errored vs timeout vs disagreement).
     */
    protected suspend fun firstNDecisive(
        count: Int,
        executor: BackendExecutor,
        timeout: Duration = Duration.INFINITE,
        progress: ProgressContext = ProgressContext.NoOp,
        block: PortfolioScope.() -> Unit,
    ): PortfolioOutcome = supervisorScope {
        val scope = PortfolioScope(this, executor)
        scope.block()

        require(scope.jobs.isNotEmpty()) { "$id: firstNDecisive requires at least one job" }
        logger.info { "$id: firstNDecisive(n=$count) over ${scope.jobs.size} job(s), timeout=$timeout" }

        val decisive = mutableListOf<VerificationResult>()
        val outcome = receiveResultsWithTimeout(scope, timeout, progress) { result ->
            if (!result.isDecisive) return@receiveResultsWithTimeout null
            decisive += result
            if (decisive.size < count) return@receiveResultsWithTimeout null
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

        if (outcome is PortfolioOutcome.NoDecision && decisive.size >= count) {
            val breakdown = decisive.joinToString(", ") { "${it.metadata.backendId}=${it.verdict}" }
            PortfolioOutcome.NoDecision("$id: firstNDecisive(n=$count) disagreement ($breakdown)")
        } else {
            outcome
        }
    }

    /**
     * Launch every job registered in [block] in parallel and wait for all of them (or [timeout]).
     * If every decisive result agrees, returns that verdict. Disagreement among decisive
     * verdicts is treated as Inconclusive with a logged conflict. Jobs that error are
     * surfaced as [PortfolioOutcome.AllErrored] when nothing else reported.
     */
    protected suspend fun all(
        executor: BackendExecutor,
        timeout: Duration = Duration.INFINITE,
        progress: ProgressContext = ProgressContext.NoOp,
        block: PortfolioScope.() -> Unit,
    ): PortfolioOutcome = supervisorScope {
        val scope = PortfolioScope(this, executor)
        scope.block()

        require(scope.jobs.isNotEmpty()) { "$id: all requires at least one job" }
        logger.info { "$id: all over ${scope.jobs.size} job(s), timeout=$timeout" }

        val collected = mutableListOf<VerificationResult>()
        val outcome = receiveResultsWithTimeout(scope, timeout, progress) { result ->
            collected += result
            null
        }

        cancelRemainingJobs()

        val decisive = collected.filter { it.isDecisive }
        when {
            decisive.isEmpty() && collected.isEmpty() -> outcome
            // AllErrored or timeout
            decisive.isEmpty() -> PortfolioOutcome.Decided(collected.first())
            decisive.map { it.verdict }.distinct().size == 1 -> PortfolioOutcome.Decided(decisive.first())
            else -> {
                val breakdown = decisive.joinToString(", ") { "${it.metadata.backendId}=${it.verdict}" }
                logger.warn { "$id: decisive verdicts disagree ($breakdown); returning Inconclusive (at least one backend must be unsound or report a bug)" }
                val reference = decisive.first()
                PortfolioOutcome.Decided(
                    VerificationResult(
                        verdict = VerificationVerdict.Inconclusive,
                        metadata = reference.metadata,
                        metrics = reference.metrics,
                        message = "Portfolio '$id' saw disagreeing decisive verdicts: $breakdown",
                    ),
                )
            }
        }
    }

    private suspend fun CoroutineScope.receiveResultsWithTimeout(
        scope: PortfolioScope,
        timeout: Duration,
        progress: ProgressContext,
        onResult: (VerificationResult) -> VerificationResult?,
    ): PortfolioOutcome {
        val results = funnelJobsIntoChannel(scope)
        val total = scope.jobs.size
        val errors = mutableListOf<Throwable>()
        val decided = withTimeoutOrNull(timeout) {
            var done = 0
            while (done < total) {
                val received = results.receive()
                done++
                val result = received.getOrNull()
                if (result == null) {
                    val failure = received.exceptionOrNull()
                    logger.warn { "$id: job errored: ${failure?.message ?: ""}" }
                    if (failure != null) errors += failure
                    continue
                }
                progress.reportProgress("result $done/$total: ${result.verdict}")
                val finalResult = onResult(result)
                if (finalResult != null) {
                    return@withTimeoutOrNull finalResult
                }
            }
            null
        }
        return when {
            decided != null -> PortfolioOutcome.Decided(decided)
            errors.size == total -> PortfolioOutcome.AllErrored(errors)
            else -> PortfolioOutcome.NoDecision("$id: no decisive result within $timeout")
        }
    }

    private fun CoroutineScope.funnelJobsIntoChannel(scope: PortfolioScope): Channel<Result<VerificationResult>> {
        val results = Channel<Result<VerificationResult>>(Channel.UNLIMITED)
        scope.jobs.forEach { deferred ->
            launch {
                val outcome = try {
                    Result.success(deferred.await())
                } catch (c: CancellationException) {
                    throw c
                } catch (t: Throwable) {
                    logger.warn { "$id: job threw ${t::class.simpleName}: ${t.message ?: ""}" }
                    Result.failure(t)
                }

                results.send(outcome)
            }
        }
        return results
    }

    private fun CoroutineScope.cancelRemainingJobs() {
        coroutineContext.cancelChildren()
    }
}
