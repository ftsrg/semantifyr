/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.theta.verification.utils

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi

@OptIn(ExperimentalCoroutinesApi::class)
suspend fun <T> List<Deferred<T>>.awaitFirstSuccess(): T {
    val firstSuccess = CompletableDeferred<T>()
    var counter = 0

    forEach { deferred ->
        deferred.invokeOnCompletion { exception ->
            synchronized(firstSuccess) {
                val id = counter++

                if (!firstSuccess.isCompleted) {
                    if (exception == null) {
                        firstSuccess.complete(deferred.getCompleted())
                    } else if (id == size - 1) {
                        firstSuccess.completeExceptionally(IllegalStateException("All executed jobs failed: ", exception))
                    }
                }
            }
        }
    }

    return firstSuccess.await()
}
