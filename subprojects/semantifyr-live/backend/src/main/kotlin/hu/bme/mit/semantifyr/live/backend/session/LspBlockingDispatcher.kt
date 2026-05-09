/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.session

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

private val lspBlockingThreadCounter = AtomicLong(0)

internal val lspBlockingDispatcher: CoroutineDispatcher = Executors.newCachedThreadPool { runnable ->
    Thread(runnable).apply {
        name = "lsp-blocking-${lspBlockingThreadCounter.incrementAndGet()}"
        isDaemon = true
    }
}.asCoroutineDispatcher()
