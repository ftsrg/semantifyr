/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.lsp.service

import com.google.inject.Singleton
import hu.bme.mit.semantifyr.logging.error
import hu.bme.mit.semantifyr.logging.loggerFactory
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

@Singleton
class SharedExecutorProvider : AutoCloseable {

    private val threadCounter = AtomicLong(0)

    private val executor = Executors.newCachedThreadPool {
        Thread(it).apply {
            name = "live-executor-${threadCounter.incrementAndGet()}"
            isDaemon = true
            uncaughtExceptionHandler = LspUncaughtHandler
        }
    }

    val dispatcher = executor.asCoroutineDispatcher()

    override fun close() {
        executor.shutdownNow()
    }
}

private object LspUncaughtHandler : Thread.UncaughtExceptionHandler {
    private val logger by loggerFactory()

    override fun uncaughtException(thread: Thread, e: Throwable) {
        logger.error(e) { "Uncaught exception on thread ${thread.name}" }
    }
}
