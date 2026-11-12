/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.lsp.service

import com.google.inject.Inject
import hu.bme.mit.semantifyr.lang.ide.server.concurrent.LockingRequestManager
import hu.bme.mit.semantifyr.live.backend.lsp.language.LanguageServices
import hu.bme.mit.semantifyr.live.backend.lsp.session.SessionRunContext
import hu.bme.mit.semantifyr.live.backend.lsp.session.SessionScoped
import hu.bme.mit.semantifyr.live.backend.lsp.session.currentSessionScopeElement
import hu.bme.mit.semantifyr.live.backend.utils.coroutineScopeCancelIndicator
import hu.bme.mit.semantifyr.live.backend.utils.currentMdcContextBlocking
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.future.future
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runInterruptible
import org.eclipse.lsp4j.ExecuteCommandParams
import org.eclipse.xtext.util.CancelIndicator
import org.eclipse.xtext.xbase.lib.Functions
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

@SessionScoped
class SessionRequestManager @Inject constructor(
    private val sessionRunContext: SessionRunContext,
    private val languageServices: LanguageServices,
    private val sessionLanguageServerAccessFactory: SessionLanguageServerAccessFactory,
) : LockingRequestManager(NoopExecutor, languageServices.operationCanceledManager) {

    private class PendingWrite(
        val job: Job,
        val validation: Job,
    )

    @Volatile
    private var pendingWrite: PendingWrite? = null

    fun executeCommand(params: ExecuteCommandParams): CompletableFuture<Any?> {
        return runRead {
            val access = sessionLanguageServerAccessFactory.create(it)
            languageServices.commandService.execute(params, access, it)
        }
    }

    @Synchronized
    override fun <V> runRead(cancellable: Functions.Function1<in CancelIndicator, out V>): CompletableFuture<V> {
        val previousWrite = pendingWrite?.job
        val coroutineContext = currentMdcContextBlocking() + currentSessionScopeElement()
        return sessionRunContext.coroutineScope.future(coroutineContext) {
            joinIgnoringCancellation(previousWrite)
            runInterruptible {
                lockProvider.acquireReadLock()
                try {
                    cancellable.apply(coroutineScopeCancelIndicator())
                } finally {
                    lockProvider.releaseReadLock()
                }
            }
        }
    }

    override fun <U, V> runWrite(
        nonCancellable: Functions.Function0<out U>,
        cancellable: Functions.Function2<in CancelIndicator, in U, out V>,
    ): CompletableFuture<V> {
        throw UnsupportedOperationException("A write is a mutation followed by a validation a newer write supersedes")
    }

    @Synchronized
    fun <U> runWrite(
        mutation: () -> U,
        validation: (CancelIndicator, U) -> Unit,
    ): CompletableFuture<U> {
        val previousWrite = pendingWrite
        previousWrite?.validation?.cancel()
        val validationJob = Job()
        val coroutineContext = currentMdcContextBlocking() + currentSessionScopeElement()
        val deferred = sessionRunContext.coroutineScope.async(coroutineContext) {
            joinIgnoringCancellation(previousWrite?.job)
            runInterruptible {
                lockProvider.acquireWriteLock()
                try {
                    val result = mutation()
                    if (validationJob.isActive) {
                        validation(validationCancelIndicator(validationJob), result)
                    }
                    result
                } finally {
                    lockProvider.releaseWriteLock()
                }
            }
        }
        pendingWrite = PendingWrite(deferred, validationJob)
        deferred.invokeOnCompletion {
            validationJob.cancel()
            synchronized(this) {
                if (pendingWrite?.job === deferred) {
                    pendingWrite = null
                }
            }
        }
        return deferred.asCompletableFuture()
    }

    private fun validationCancelIndicator(validationJob: Job): CancelIndicator {
        return CancelIndicator {
            !validationJob.isActive || !sessionRunContext.coroutineScope.isActive
        }
    }

    private suspend fun joinIgnoringCancellation(job: Job?) {
        if (job == null) {
            return
        }
        try {
            job.join()
        } catch (_: CancellationException) {
        }
    }
}

private object NoopExecutor : AbstractExecutorService() {
    override fun shutdown() {
    }

    override fun shutdownNow(): MutableList<Runnable> {
        return mutableListOf()
    }

    override fun isShutdown(): Boolean {
        return false
    }

    override fun isTerminated(): Boolean {
        return false
    }

    override fun awaitTermination(timeout: Long, unit: TimeUnit): Boolean {
        return true
    }

    override fun execute(command: Runnable) {
    }
}
