/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.verification.portfolio

import com.google.inject.Injector
import hu.bme.mit.semantifyr.backend.BackendMetrics
import hu.bme.mit.semantifyr.backend.BackendVerificationRequest
import hu.bme.mit.semantifyr.backend.BackendVerificationResult
import hu.bme.mit.semantifyr.backend.VerificationMetadata
import hu.bme.mit.semantifyr.backend.VerificationVerdict
import hu.bme.mit.semantifyr.backend.execution.AvailabilityReport
import hu.bme.mit.semantifyr.backend.execution.ExecutionEnvironment
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
import kotlin.time.Instant

class PortfolioScope internal constructor(
    private val coroutineScope: CoroutineScope,
    val gate: ConcurrencyGate,
) {
    internal val jobs = mutableListOf<Deferred<BackendVerificationResult>>()

    fun async(block: suspend () -> BackendVerificationResult) {
        jobs += coroutineScope.async {
            block()
        }
    }
}

fun BackendVerificationRequest.withSubPath(subpath: String): BackendVerificationRequest {
    return this.copy(artifactOutputPath = artifactOutputPath.resolve(subpath))
}

sealed interface PortfolioOutcome {
    data class Decided(val result: BackendVerificationResult) : PortfolioOutcome {
        override fun toBackendVerificationResult(startedAt: Instant): BackendVerificationResult {
            return result.copy(
                metadata = result.metadata.copy(startedAt = startedAt),
            )
        }
    }

    data class AllErrored(val exceptions: List<Throwable>) : PortfolioOutcome {
        override fun toBackendVerificationResult(startedAt: Instant): BackendVerificationResult {
            val firstMessage = exceptions.firstOrNull()?.let {
                "${it::class.simpleName}: ${it.message ?: ""}"
            } ?: "(no message)"
            val trailingCount = (exceptions.size - 1).coerceAtLeast(0)
            val summary = if (trailingCount > 0) {
                "$firstMessage (+$trailingCount more)"
            } else {
                firstMessage
            }
            return BackendVerificationResult(
                metadata = VerificationMetadata(
                    backendId = null,
                    startedAt = startedAt,
                ),
                verdict = VerificationVerdict.Errored,
                metrics = BackendMetrics(),
                message = "All portfolio jobs errored: $summary",
            )
        }
    }

    data class NoDecision(val reason: String) : PortfolioOutcome {
        override fun toBackendVerificationResult(startedAt: Instant): BackendVerificationResult {
            return BackendVerificationResult(
                metadata = VerificationMetadata(
                    backendId = null,
                    startedAt = startedAt,
                ),
                verdict = VerificationVerdict.Inconclusive,
                metrics = BackendMetrics(),
                message = reason,
            )
        }
    }

    data class AllNotSupported(val reason: String) : PortfolioOutcome {
        override fun toBackendVerificationResult(startedAt: Instant): BackendVerificationResult {
            return BackendVerificationResult(
                metadata = VerificationMetadata(
                    backendId = null,
                    startedAt = startedAt,
                ),
                verdict = VerificationVerdict.NotSupported,
                metrics = BackendMetrics(),
                message = reason,
            )
        }
    }

    fun toBackendVerificationResult(startedAt: Instant): BackendVerificationResult
}

abstract class VerificationPortfolio {

    protected val logger by loggerFactory()

    abstract val id: String
    abstract val displayName: String
    abstract val description: String

    abstract fun availability(environment: ExecutionEnvironment = ExecutionEnvironment.Empty): AvailabilityReport

    abstract suspend fun verify(
        parentInjector: Injector,
        request: BackendVerificationRequest,
        gate: ConcurrencyGate,
        environment: ExecutionEnvironment,
        progress: ProgressContext,
    ): BackendVerificationResult

    /**
     * Launch every job registered in [block] in parallel, returns the first decisive result and cancels every still-running child.
     * When no decisive result arrives in time the outcome distinguishes "all jobs errored" from "some jobs still pending at timeout" so callers can surface the right verdict (Errored vs Inconclusive).
     */
    protected suspend fun firstDecisive(
        gate: ConcurrencyGate,
        timeout: Duration = Duration.INFINITE,
        progress: ProgressContext = ProgressContext.NoOp,
        block: PortfolioScope.() -> Unit,
    ): PortfolioOutcome = supervisorScope {
        val scope = PortfolioScope(this, gate)
        scope.block()

        require(scope.jobs.isNotEmpty()) { "$id: firstDecisive requires at least one job" }
        logger.info { "$id: firstDecisive over ${scope.jobs.size} job(s), timeout=$timeout" }

        val outcome = receiveResultsWithTimeout(scope, timeout, progress) {
            if (it.isDecisive) {
                logger.info { "$id: job produced decisive ${it.verdict}" }
                it
            } else {
                null
            }
        }

        cancelRemainingJobs()
        outcome
    }

    /**
     * Launch every job registered in [block] in parallel. Waits for [count] decisive results.
     * If they all agree, returns that verdict.
     * On disagreement, fewer-than-[count] decisive results within [timeout], or too many job errors, returns a non-decided outcome.
     */
    protected suspend fun firstNDecisive(
        count: Int,
        gate: ConcurrencyGate,
        timeout: Duration = Duration.INFINITE,
        progress: ProgressContext = ProgressContext.NoOp,
        block: PortfolioScope.() -> Unit,
    ): PortfolioOutcome = supervisorScope {
        val scope = PortfolioScope(this, gate)
        scope.block()

        require(scope.jobs.isNotEmpty()) { "$id: firstNDecisive requires at least one job" }
        logger.info { "$id: firstNDecisive(n=$count) over ${scope.jobs.size} job(s), timeout=$timeout" }

        val decisive = mutableListOf<BackendVerificationResult>()
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
     * If every decisive result agrees, returns that verdict.
     * Disagreement among decisive verdicts is treated as Inconclusive with a logged conflict.
     * Jobs that error are surfaced as [PortfolioOutcome.AllErrored] when nothing else reported.
     */
    protected suspend fun all(
        gate: ConcurrencyGate,
        timeout: Duration = Duration.INFINITE,
        progress: ProgressContext = ProgressContext.NoOp,
        block: PortfolioScope.() -> Unit,
    ): PortfolioOutcome = supervisorScope {
        val scope = PortfolioScope(this, gate)
        scope.block()

        require(scope.jobs.isNotEmpty()) { "$id: all requires at least one job" }
        logger.info { "$id: all over ${scope.jobs.size} job(s), timeout=$timeout" }

        val collected = mutableListOf<BackendVerificationResult>()
        val outcome = receiveResultsWithTimeout(scope, timeout, progress) { result ->
            collected += result
            null
        }

        cancelRemainingJobs()

        val decisive = collected.filter { it.isDecisive }
        when {
            decisive.isEmpty() && collected.isEmpty() -> outcome
            decisive.isEmpty() && collected.all { it.verdict == VerificationVerdict.NotSupported } -> {
                val backends = collected.joinToString(", ") { it.metadata.backendId ?: "?" }
                PortfolioOutcome.AllNotSupported("$id: every backend reported NotSupported ($backends)")
            }
            decisive.isEmpty() -> PortfolioOutcome.Decided(collected.first())
            decisive.map { it.verdict }.distinct().size == 1 -> PortfolioOutcome.Decided(decisive.first())
            else -> {
                val breakdown = decisive.joinToString(", ") { "${it.metadata.backendId}=${it.verdict}" }
                logger.warn { "$id: decisive verdicts disagree ($breakdown); returning Inconclusive (at least one backend must be unsound or report a bug)" }
                val reference = decisive.first()
                PortfolioOutcome.Decided(
                    BackendVerificationResult(
                        verdict = VerificationVerdict.Inconclusive,
                        metadata = VerificationMetadata(
                            backendId = null,
                            startedAt = reference.metadata.startedAt,
                        ),
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
        onResult: (BackendVerificationResult) -> BackendVerificationResult?,
    ): PortfolioOutcome {
        val results = funnelJobsIntoChannel(scope)
        val total = scope.jobs.size
        val errors = mutableListOf<Throwable>()
        val collected = mutableListOf<BackendVerificationResult>()
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
                collected += result
                val finalResult = onResult(result)
                if (finalResult != null) {
                    return@withTimeoutOrNull finalResult
                }
            }
            null
        }
        val allNotSupported = errors.isEmpty() &&
            collected.isNotEmpty() &&
            collected.all { it.verdict == VerificationVerdict.NotSupported }
        return when {
            decided != null -> PortfolioOutcome.Decided(decided)
            allNotSupported -> {
                val backends = collected.joinToString(", ") { it.metadata.backendId ?: "?" }
                PortfolioOutcome.AllNotSupported("$id: every backend reported NotSupported ($backends)")
            }
            errors.size == total -> PortfolioOutcome.AllErrored(errors)
            else -> PortfolioOutcome.NoDecision("$id: no decisive result within $timeout")
        }
    }

    private fun CoroutineScope.funnelJobsIntoChannel(scope: PortfolioScope): Channel<Result<BackendVerificationResult>> {
        val results = Channel<Result<BackendVerificationResult>>(Channel.UNLIMITED)
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
