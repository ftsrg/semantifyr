/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.verification.portfolio

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.seconds

class BackendExecutorTest {

    @Test
    suspend fun `LimitedBackendExecutor runs up to maxConcurrency in parallel`() {
        val executor = LimitedBackendExecutor(maxConcurrency = 2)
        val concurrent = AtomicInteger(0)
        val peak = AtomicInteger(0)
        val release = CompletableDeferred<Unit>()

        coroutineScope {
            val jobs = (1..5).map {
                async(Dispatchers.Default) {
                    executor.withPermit {
                        val now = concurrent.incrementAndGet()
                        peak.updateAndGet { maxOf(it, now) }
                        release.await()
                        concurrent.decrementAndGet()
                    }
                }
            }

            waitUntil { concurrent.get() == 2 }
            assertThat(peak.get()).isEqualTo(2)

            release.complete(Unit)
            jobs.awaitAll()
        }

        assertThat(peak.get()).isEqualTo(2)
    }

    @Test
    suspend fun `LimitedBackendExecutor serializes with maxConcurrency 1`() {
        val executor = LimitedBackendExecutor(maxConcurrency = 1)
        val order = mutableListOf<Int>()
        val inside = AtomicInteger(0)
        val observedMax = AtomicInteger(0)

        coroutineScope {
            val jobs = (1..4).map { i ->
                async(Dispatchers.Default) {
                    executor.withPermit {
                        val now = inside.incrementAndGet()
                        observedMax.updateAndGet { maxOf(it, now) }
                        delay(10)
                        synchronized(order) { order += i }
                        inside.decrementAndGet()
                    }
                }
            }
            jobs.awaitAll()
        }

        assertThat(observedMax.get()).isEqualTo(1)
        assertThat(order).hasSize(4)
    }

    @Test
    suspend fun `CappedBackendExecutor caps beneath an unlimited outer`() {
        val outer = LimitedBackendExecutor(maxConcurrency = 10)
        val capped = outer.capped(maxConcurrency = 2)

        val concurrent = AtomicInteger(0)
        val peak = AtomicInteger(0)
        val release = CompletableDeferred<Unit>()

        coroutineScope {
            val jobs = (1..5).map {
                async(Dispatchers.Default) {
                    capped.withPermit {
                        val now = concurrent.incrementAndGet()
                        peak.updateAndGet { maxOf(it, now) }
                        release.await()
                        concurrent.decrementAndGet()
                    }
                }
            }

            waitUntil { concurrent.get() == 2 }
            assertThat(peak.get()).isEqualTo(2)

            release.complete(Unit)
            jobs.awaitAll()
        }

        assertThat(peak.get()).isEqualTo(2)
    }

    @Test
    suspend fun `CappedBackendExecutor respects the stricter of outer and inner limits`() {
        val outer = LimitedBackendExecutor(maxConcurrency = 1)
        val capped = outer.capped(maxConcurrency = 5)

        val concurrent = AtomicInteger(0)
        val peak = AtomicInteger(0)

        coroutineScope {
            val jobs = (1..4).map {
                async(Dispatchers.Default) {
                    capped.withPermit {
                        val now = concurrent.incrementAndGet()
                        peak.updateAndGet { maxOf(it, now) }
                        delay(10)
                        concurrent.decrementAndGet()
                    }
                }
            }
            jobs.awaitAll()
        }

        assertThat(peak.get()).isEqualTo(1)
    }

    @Test
    suspend fun `withPermit returns the block's value`() {
        val executor = LimitedBackendExecutor(maxConcurrency = 1)
        val value = executor.withPermit { 42 }
        assertThat(value).isEqualTo(42)
    }
}

private suspend fun waitUntil(timeout: kotlin.time.Duration = 2.seconds, condition: () -> Boolean) {
    withTimeout(timeout) {
        while (!condition()) {
            delay(5)
        }
    }
}

private suspend fun <T> List<Deferred<T>>.awaitAll(): List<T> {
    return map { it.await() }
}
