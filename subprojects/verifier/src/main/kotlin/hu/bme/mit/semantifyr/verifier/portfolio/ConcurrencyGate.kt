/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.verifier.portfolio

import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

interface ConcurrencyGate {
    val slots: Int

    suspend fun <T> withPermit(block: suspend () -> T): T
}

class LimitedConcurrencyGate(
    maxConcurrency: Int,
) : ConcurrencyGate {

    init {
        require(maxConcurrency >= 1) { "maxConcurrency must be >= 1, got $maxConcurrency" }
    }

    override val slots = maxConcurrency

    private val gate = Semaphore(maxConcurrency)

    override suspend fun <T> withPermit(block: suspend () -> T): T {
        return gate.withPermit {
            block()
        }
    }
}
