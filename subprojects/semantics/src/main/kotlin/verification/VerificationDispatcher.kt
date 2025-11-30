/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.semantics.verification

import com.google.inject.Singleton
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

@Singleton
class VerificationDispatcher {

    val limitedParallelism = 4
    val semaphore = Semaphore(limitedParallelism)

    suspend inline fun <T> execute(crossinline block: suspend () -> T): T {
        return semaphore.withPermit {
            block()
        }
    }

    inline fun <T> runBlocking(crossinline block: suspend () -> T): T {
        return kotlinx.coroutines.runBlocking {
            execute {
                block()
            }
        }
    }

}
