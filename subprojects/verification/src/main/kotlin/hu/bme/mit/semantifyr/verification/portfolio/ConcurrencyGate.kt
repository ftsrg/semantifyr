/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.verification.portfolio

import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

interface ConcurrencyGate {
    suspend fun <T> withPermit(block: suspend () -> T): T
}

class LimitedConcurrencyGate(
    maxConcurrency: Int,
) : ConcurrencyGate {

    init {
        require(maxConcurrency >= 1) { "maxConcurrency must be >= 1, got $maxConcurrency" }
    }

    private val gate = Semaphore(maxConcurrency)

    override suspend fun <T> withPermit(block: suspend () -> T): T {
        return gate.withPermit {
            block()
        }
    }
}
