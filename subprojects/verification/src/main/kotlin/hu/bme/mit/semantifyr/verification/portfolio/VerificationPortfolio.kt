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
import hu.bme.mit.semantifyr.logging.debug
import hu.bme.mit.semantifyr.logging.info
import hu.bme.mit.semantifyr.logging.loggerFactory
import hu.bme.mit.semantifyr.verification.ProgressContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.slf4j.Logger
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
     * and cancels the rest. Returns null if no decisive result within [timeout].
     *
     * As each result arrives, emits `"result N/M: verdict"` on [progress].
     */
    protected suspend fun firstDecisive(
        executor: BackendExecutor,
        timeout: Duration = Duration.INFINITE,
        progress: ProgressContext = ProgressContext.NoOp,
        block: PortfolioScope.() -> Unit,
    ) = coroutineScope {
        val scope = PortfolioScope(this, executor)
        scope.block()

        require(scope.jobs.isNotEmpty()) { "$id: firstDecisive requires at least one job" }
        logger.debug { "$id: firstDecisive over ${scope.jobs.size} job(s), timeout=$timeout" }

        val results = Channel<VerificationResult>(Channel.UNLIMITED)
        scope.jobs.forEach {
            launch {
                results.send(it.await())
            }
        }

        withTimeoutOrNull(timeout) {
            var fallback: VerificationResult? = null
            val total = scope.jobs.size
            repeat(total) { i ->
                val result = results.receive()
                progress.reportProgress("result ${i + 1}/$total: ${result.verdict}")
                if (result.isDecisive) {
                    logger.info { "$id: job produced decisive ${result.verdict}" }
                    return@withTimeoutOrNull result
                }
                fallback = result
            }
            fallback
        }
    }

    /**
     * Launch every job registered in [block] in parallel. Waits for [count] decisive results.
     * If they all agree, returns that verdict. Returns null on disagreement or if fewer
     * than [count] decisive results arrive within [timeout]. The caller decides how to interpret
     * that (typically mapping to [hu.bme.mit.semantifyr.backend.VerificationVerdict.Inconclusive]).
     */
    protected suspend fun firstNDecisive(
        count: Int,
        executor: BackendExecutor,
        timeout: Duration = Duration.INFINITE,
        progress: ProgressContext = ProgressContext.NoOp,
        block: PortfolioScope.() -> Unit,
    ) = coroutineScope {
        val scope = PortfolioScope(this, executor)
        scope.block()

        require(scope.jobs.isNotEmpty()) { "$id: firstNDecisive requires at least one job" }
        logger.debug { "$id: firstNDecisive(n=$count) over ${scope.jobs.size} job(s), timeout=$timeout" }

        val results = Channel<VerificationResult>(Channel.UNLIMITED)
        scope.jobs.forEach {
            launch {
                results.send(it.await())
            }
        }

        withTimeoutOrNull(timeout) {
            val decisive = mutableListOf<VerificationResult>()
            val total = scope.jobs.size
            repeat(total) { i ->
                val result = results.receive()
                progress.reportProgress("result ${i + 1}/$total: ${result.verdict}")
                if (result.isDecisive) {
                    decisive += result
                    if (decisive.size >= count) {
                        val allAgree = decisive.map { it.verdict }.distinct().size == 1
                        return@withTimeoutOrNull if (allAgree) {
                            decisive.first()
                        } else {
                            null
                        }
                    }
                }
            }
            null
        }
    }

    /**
     * Launch every job registered in [block] in parallel. All must complete within [timeout].
     * If all agree on a decisive verdict, returns it. Otherwise, it returns the first decisive
     * result, or null if none are decisive.
     */
    protected suspend fun all(
        executor: BackendExecutor,
        timeout: Duration = Duration.INFINITE,
        progress: ProgressContext = ProgressContext.NoOp,
        block: PortfolioScope.() -> Unit,
    ) = coroutineScope {
        val scope = PortfolioScope(this, executor)
        scope.block()

        require(scope.jobs.isNotEmpty()) { "$id: all requires at least one job" }
        logger.debug { "$id: all over ${scope.jobs.size} job(s), timeout=$timeout" }

        val results = Channel<VerificationResult>(Channel.UNLIMITED)
        scope.jobs.forEach {
            launch {
                results.send(it.await())
            }
        }

        withTimeoutOrNull(timeout) {
            val collected = mutableListOf<VerificationResult>()
            val total = scope.jobs.size
            repeat(total) { i ->
                val result = results.receive()
                progress.reportProgress("result ${i + 1}/$total: ${result.verdict}")
                collected += result
            }
            val decisive = collected.filter { it.isDecisive }
            if (decisive.isEmpty()) {
                return@withTimeoutOrNull collected.firstOrNull()
            }
            val allAgree = decisive.map { it.verdict }.distinct().size == 1
            if (allAgree) {
                decisive.first()
            } else {
                collected.firstOrNull { it.isDecisive }
            }
        }
    }

}
