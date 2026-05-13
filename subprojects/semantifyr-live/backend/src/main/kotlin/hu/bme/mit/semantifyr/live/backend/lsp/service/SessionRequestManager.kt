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
import kotlinx.coroutines.runInterruptible
import org.eclipse.xtext.util.CancelIndicator
import org.eclipse.xtext.xbase.lib.Functions
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

@SessionScoped
class SessionRequestManager @Inject constructor(
    private val sessionRunContext: SessionRunContext,
    languageServices: LanguageServices,
) : LockingRequestManager(NoopExecutor, languageServices.operationCanceledManager) {

    @Volatile
    private var pendingWrite: Job? = null

    @Synchronized
    override fun <V> runRead(cancellable: Functions.Function1<in CancelIndicator, out V>): CompletableFuture<V> {
        val previousWrite = pendingWrite
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

    @Synchronized
    override fun <U, V> runWrite(
        nonCancellable: Functions.Function0<out U>,
        cancellable: Functions.Function2<in CancelIndicator, in U, out V>,
    ): CompletableFuture<V> {
        val previousWrite = pendingWrite
        previousWrite?.cancel()
        val coroutineContext = currentMdcContextBlocking() + currentSessionScopeElement()
        val deferred = sessionRunContext.coroutineScope.async(coroutineContext) {
            joinIgnoringCancellation(previousWrite)
            runInterruptible {
                lockProvider.acquireWriteLock()
                try {
                    val intermediate = nonCancellable.apply()
                    cancellable.apply(coroutineScopeCancelIndicator(), intermediate)
                } finally {
                    lockProvider.releaseWriteLock()
                }
            }
        }
        pendingWrite = deferred
        deferred.invokeOnCompletion {
            synchronized(this) {
                if (pendingWrite === deferred) {
                    pendingWrite = null
                }
            }
        }
        return deferred.asCompletableFuture()
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

inline fun <V> SessionRequestManager.runWrite(
    crossinline cancellable: (CancelIndicator) -> V,
): CompletableFuture<V> {
    return runWrite({}, { cancelIndicator, _ ->
        cancellable(cancelIndicator)
    })
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
