/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.theta.verification.execution

import com.google.inject.Inject
import hu.bme.mit.semantifyr.backends.theta.verification.utils.awaitFirstSuccess
import hu.bme.mit.semantifyr.semantics.transformation.ProgressContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlin.io.path.absolute

class ThetaPortfolioRunner {

    private val logger = LoggerFactory.getLogger(ThetaPortfolioRunner::class.java)

    val parameters = listOf(
        "CEGAR --domain EXPL --flatten-depth 0 --refinement SEQ_ITP --maxenum 250 --initprec CTRL --stacktrace",
        "CEGAR --domain EXPL_PRED_COMBINED --flatten-depth 0 --autoexpl NEWOPERANDS --initprec CTRL --stacktrace",
        "CEGAR --domain PRED_CART --flatten-depth 0 --refinement SEQ_ITP --stacktrace",
        "BOUNDED --flatten-depth 0 --variant KINDUCTION --stacktrace",
    )
    val timeout = 5L
    val timeUnit = TimeUnit.MINUTES
    val checkAllResults = false

    @Inject
    private lateinit var thetaVerificationExecutor: ThetaVerificationExecutor

    private suspend fun runWorkflow(workingDirectory: String, name: String) = supervisorScope {
        val jobs = parameters.indices.map { index ->
            async {
                val thetaVerificationSpecification = ThetaVerificationSpecification(workingDirectory, name, index, parameters[index], timeout, timeUnit)
                thetaVerificationExecutor.execute(thetaVerificationSpecification)
            }
        }

        // since verification is expensive, this hook ensures all verifiers are disposed of upon termination.
        // putting it here to prevent forgetting it in the specific verifier executors.
        // executors must only ensure to handle cancellation correctly.
        val shutdownThread = Thread {
            runBlocking {
                cancelAllModelCheckers(jobs)
            }
        }

        Runtime.getRuntime().addShutdownHook(shutdownThread)

        try {
            jobs.awaitFirstSuccess()
        } finally {
            if (checkAllResults) {
                checkAllModelCheckerResults(jobs)
            }

            cancelAllModelCheckers(jobs)

            Runtime.getRuntime().removeShutdownHook(shutdownThread)
        }
    }

    private suspend fun checkAllModelCheckerResults(jobs: List<Deferred<ThetaVerificationResult>>) {
        var safe = false
        var unsafe = false

        jobs.forEach {
            val result = it.await()

            if (result is ThetaSafeVerificationResult) {
                logger.debug("Safe result for ${result.runtimeDetails.id}")
                safe = true
            } else if (result is ThetaUnsafeVerificationResult) {
                logger.debug("Unsafe result for ${result.runtimeDetails.id}")
                unsafe = true
            }
        }

        if (safe && unsafe) {
            logger.error("Conflicting results!")
        }
    }

    private suspend fun cancelAllModelCheckers(jobs: List<Deferred<ThetaVerificationResult>>) {
        jobs.forEach {
            it.cancel()
        }
        jobs.joinAll()
    }

    private fun CoroutineScope.startCancellationChecker(progressContext: ProgressContext): Deferred<Unit> {
        return async(Dispatchers.IO) {
            while (true) {
                try {
                    progressContext.checkIsCancelled()
                } catch (c: CancellationException) {
                    this@startCancellationChecker.coroutineContext.cancel(c)
                    cancel(c)
                }
                delay(100)
            }
        }
    }

    fun run(workingDirectory: String, name: String, progressContext: ProgressContext) = runBlocking {
        val absoluteDirectory = Path.of(workingDirectory).absolute().toString()

        val cancellationChecker = startCancellationChecker(progressContext)

        val result = runWorkflow(absoluteDirectory, name)

        cancellationChecker.cancel()

        result
    }

}
