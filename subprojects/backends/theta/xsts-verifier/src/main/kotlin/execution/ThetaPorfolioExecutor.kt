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
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlin.io.path.absolute

class ThetaPortfolioRunner {
    val parameters = listOf(
        "CEGAR --domain EXPL --flatten-depth 0 --refinement SEQ_ITP --maxenum 250 --initprec CTRL --stacktrace",
        "CEGAR --domain EXPL_PRED_COMBINED --flatten-depth 0 --autoexpl NEWOPERANDS --initprec CTRL --stacktrace",
        "CEGAR --domain PRED_CART --flatten-depth 0 --refinement SEQ_ITP --stacktrace",
        "BOUNDED --flatten-depth 0 --variant KINDUCTION --stacktrace",
    )
    val timeout = 5L
    val timeUnit = TimeUnit.MINUTES

    @Inject
    private lateinit var thetaVerificationExecutor: ThetaVerificationExecutor

    private suspend fun runWorkflow(workingDirectory: String, name: String) = supervisorScope {
        val jobs = parameters.indices.map { index ->
            async {
                val thetaVerificationSpecification = ThetaVerificationSpecification(workingDirectory, name, index, parameters[index], timeout, timeUnit)
                thetaVerificationExecutor.execute(thetaVerificationSpecification)
            }
        }

        try {
            jobs.awaitFirstSuccess()
        } finally {
            jobs.forEach {
                it.cancelAndJoin()
            }
        }
    }

    private fun CoroutineScope.startCancellationChecker(progressContext: ProgressContext): Deferred<Unit> {
        return async {
            while (true) {
                try {
                    progressContext.checkIsCancelled()
                } catch (c: CancellationException) {
                    this@startCancellationChecker.coroutineContext.cancel(c)
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
