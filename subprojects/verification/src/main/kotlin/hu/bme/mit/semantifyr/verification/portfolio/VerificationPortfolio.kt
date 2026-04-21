/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.verification.portfolio

import hu.bme.mit.semantifyr.backend.AvailabilityReport
import hu.bme.mit.semantifyr.backend.ExecutionEnvironment
import hu.bme.mit.semantifyr.backend.VerificationRequest
import hu.bme.mit.semantifyr.backend.VerificationResult
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

    /**
     * Start a parallel verification job.
     * Use [executor] inside the block for concurrency gating.
     */
    fun async(block: suspend () -> VerificationResult) {
        jobs += coroutineScope.async {
            block()
        }
    }
}

fun VerificationRequest.withSubPath(subpath: String): VerificationRequest {
    return this.copy(artifactOutputPath = artifactOutputPath.resolve(subpath))
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
     * and cancels every still-running child. If no job produces a decisive result within
     * [timeout] returns the best non-decisive result observed, or `null` if every
     * job failed with an exception.
     */
    protected suspend fun firstDecisive(
        executor: BackendExecutor,
        timeout: Duration = Duration.INFINITE,
        progress: ProgressContext = ProgressContext.NoOp,
        block: PortfolioScope.() -> Unit,
    ): VerificationResult? = supervisorScope {
        val scope = PortfolioScope(this, executor)
        scope.block()

        require(scope.jobs.isNotEmpty()) { "$id: firstDecisive requires at least one job" }
        logger.info { "$id: firstDecisive over ${scope.jobs.size} job(s), timeout=$timeout" }

        val output = receiveResultsWithTimeout(scope, timeout, progress) { result ->
            if (result.isDecisive) {
                logger.info { "$id: job produced decisive ${result.verdict}" }
                result
            } else {
                null
            }
        }

        cancelRemainingJobs()
        output
    }

    /**
     * Launch every job registered in [block] in parallel. Waits for [count] decisive results.
     * If they all agree, returns that verdict. Returns `null` on disagreement, if fewer than
     * [count] decisive results arrive within [timeout], or if too many jobs fail with exceptions
     * to reach [count]. The caller decides how to interpret a null (typically mapping to
     * [hu.bme.mit.semantifyr.backend.VerificationVerdict.Inconclusive]).
     */
    protected suspend fun firstNDecisive(
        count: Int,
        executor: BackendExecutor,
        timeout: Duration = Duration.INFINITE,
        progress: ProgressContext = ProgressContext.NoOp,
        block: PortfolioScope.() -> Unit,
    ): VerificationResult? = supervisorScope {
        val scope = PortfolioScope(this, executor)
        scope.block()

        require(scope.jobs.isNotEmpty()) { "$id: firstNDecisive requires at least one job" }
        logger.info { "$id: firstNDecisive(n=$count) over ${scope.jobs.size} job(s), timeout=$timeout" }

        val decisive = mutableListOf<VerificationResult>()
        val output = receiveResultsWithTimeout(scope, timeout, progress) { result ->
            if (!result.isDecisive) return@receiveResultsWithTimeout null
            decisive += result
            if (decisive.size < count) return@receiveResultsWithTimeout null
            val allAgree = decisive.map { it.verdict }.distinct().size == 1
            if (allAgree) {
                decisive.first()
            } else {
                val breakdown = decisive.joinToString(", ") { "${it.metadata.backendId}=${it.verdict}" }
                logger.warn { "$id: firstNDecisive(n=$count) disagreement among the first $count decisive results ($breakdown); returning null so the caller can map to Inconclusive" }
                null
            }
        }

        cancelRemainingJobs()
        output
    }

    /**
     * Launch every job registered in [block] in parallel and wait for all of them (or [timeout]).
     * If every decisive result agrees, returns that verdict; if decisive results disagree
     * (e.g. one backend returns Passed and another Failed), returns Inconclusive and logs the
     * conflict, because at most one of the disagreeing verdicts can be sound. Otherwise returns
     * the first non-decisive result, or `null` if every job failed with an exception.
     * On timeout, outstanding jobs are cancelled.
     */
    protected suspend fun all(
        executor: BackendExecutor,
        timeout: Duration = Duration.INFINITE,
        progress: ProgressContext = ProgressContext.NoOp,
        block: PortfolioScope.() -> Unit,
    ): VerificationResult? = supervisorScope {
        val scope = PortfolioScope(this, executor)
        scope.block()

        require(scope.jobs.isNotEmpty()) { "$id: all requires at least one job" }
        logger.info { "$id: all over ${scope.jobs.size} job(s), timeout=$timeout" }

        val collected = mutableListOf<VerificationResult>()
        receiveResultsWithTimeout(scope, timeout, progress) { result ->
            collected += result
            null
        }

        cancelRemainingJobs()

        val decisive = collected.filter { it.isDecisive }
        when {
            decisive.isEmpty() -> collected.firstOrNull()
            decisive.map { it.verdict }.distinct().size == 1 -> decisive.first()
            else -> {
                val breakdown = decisive.joinToString(", ") { "${it.metadata.backendId}=${it.verdict}" }
                logger.warn { "$id: decisive verdicts disagree ($breakdown); returning Inconclusive (at least one backend must be unsound or report a bug)" }
                val reference = decisive.first()
                VerificationResult(
                    verdict = VerificationVerdict.Inconclusive,
                    metadata = reference.metadata,
                    metrics = reference.metrics,
                    message = "Portfolio '$id' saw disagreeing decisive verdicts: $breakdown",
                )
            }
        }
    }

    /**
     * Wires the [scope]'s jobs onto a channel and drains the channel, feeding each successful
     * result to [onResult]. Returns the first non-null value [onResult] produces (short-circuiting
     * the remaining results) or `null` if [timeout] fires or every job has reported without a
     * decision. Failures and cancellations are logged and skipped so one errored backend can't
     * starve the portfolio.
     */
    private suspend fun CoroutineScope.receiveResultsWithTimeout(
        scope: PortfolioScope,
        timeout: Duration,
        progress: ProgressContext,
        onResult: (VerificationResult) -> VerificationResult?,
    ): VerificationResult? {
        val results = funnelJobsIntoChannel(scope)
        val total = scope.jobs.size
        return withTimeoutOrNull(timeout) {
            var done = 0
            while (done < total) {
                val received = results.receive()
                done++
                val result = received.getOrNull()
                if (result == null) {
                    logger.warn { "$id: job errored: ${received.exceptionOrNull()?.message ?: ""}" }
                    continue
                }
                progress.reportProgress("result $done/$total: ${result.verdict}")
                onResult(result)?.let { return@withTimeoutOrNull it }
            }
            null
        }
    }

    /**
     * Runs the jobs in the Scope by routing their results into a channel -> simple handling of concurrent results
     */
    private fun CoroutineScope.funnelJobsIntoChannel(scope: PortfolioScope): Channel<Result<VerificationResult>> {
        // Bound the channel at the job count: every job will enqueue exactly
        // one result, and the drainer reads all of them, so this capacity
        // never forces a sender to suspend. A bounded channel makes the
        // intent explicit vs an unlimited buffer that grows with job count.
        val results = Channel<Result<VerificationResult>>(scope.jobs.size)
        scope.jobs.forEach { deferred ->
            launch {
                val outcome = try {
                    Result.success(deferred.await())
                } catch (c: CancellationException) {
                    throw c
                } catch (e: Exception) {
                    logger.warn { "$id: job threw ${e::class.simpleName}: ${e.message ?: ""}" }
                    Result.failure(e)
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
