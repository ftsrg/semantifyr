/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.verification.portfolio

import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

interface BackendExecutor {
    suspend fun <T> withPermit(block: suspend () -> T): T

    fun capped(maxConcurrency: Int): BackendExecutor {
        return CappedBackendExecutor(this, maxConcurrency)
    }
}

class LimitedBackendExecutor(
    maxConcurrency: Int
) : BackendExecutor {

    init {
        require(maxConcurrency >= 1) { "maxConcurrency must be >= 1, got $maxConcurrency" }
    }

    private val gate = Semaphore(maxConcurrency)

    override suspend fun <T> withPermit(block: suspend () -> T): T {
        return gate.withPermit { block() }
    }

}

class CappedBackendExecutor(
    private val outer: BackendExecutor,
    maxConcurrency: Int
) : BackendExecutor {

    init {
        require(maxConcurrency >= 1) { "maxConcurrency must be >= 1, got $maxConcurrency" }
    }

    private val gate = Semaphore(maxConcurrency)

    override suspend fun <T> withPermit(block: suspend () -> T): T {
        return outer.withPermit {
            gate.withPermit {
                block()
            }
        }
    }

}
