/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.verification.portfolio

import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Concurrency gate for running verification jobs. Each call to [withPermit]
 * suspends until a permit is free, runs [block], and releases the permit.
 *
 * Composition is via [capped]: the returned executor enforces a tighter bound
 * nested inside the outer one. Lock order is always outer-before-inner, so
 * no deadlock cycle can form even with multiple nested layers.
 */
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

/**
 * Wraps [outer] with a tighter inner cap. `withPermit` acquires [outer] first,
 * then the inner gate. Since every nested wrapper applies the same order,
 * no deadlock cycle is possible.
 *
 * If [maxConcurrency] is greater than or equal to the effective outer cap,
 * the inner gate is redundant - the outer gate dominates. The wrapper is
 * still well-behaved in that case; consumers just pay an extra unused
 * suspend point per permit acquisition.
 */
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
