/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.verification.portfolio

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BackendExecutorTest {

    @Test
    fun `LimitedBackendExecutor runs up to maxConcurrency in parallel`() = runTest {
        val executor = LimitedBackendExecutor(maxConcurrency = 2)
        val entered = Channel<Unit>(Channel.UNLIMITED)
        val release = CompletableDeferred<Unit>()

        val jobs = (1..5).map {
            async {
                executor.withPermit {
                    entered.send(Unit)
                    release.await()
                }
            }
        }

        runCurrent()

        assertThat(entered.tryReceive().isSuccess).isTrue
        assertThat(entered.tryReceive().isSuccess).isTrue
        assertThat(entered.tryReceive().isFailure).isTrue

        release.complete(Unit)
        jobs.awaitAll()
    }

    @Test
    fun `LimitedBackendExecutor serializes with maxConcurrency 1`() = runTest {
        val executor = LimitedBackendExecutor(maxConcurrency = 1)
        val entered = Channel<Unit>(Channel.UNLIMITED)
        val release = Channel<Unit>(Channel.UNLIMITED)

        val jobs = (1..4).map {
            async {
                executor.withPermit {
                    entered.send(Unit)
                    release.receive()
                }
            }
        }

        repeat(4) {
            runCurrent()
            assertThat(entered.tryReceive().isSuccess).isTrue
            assertThat(entered.tryReceive().isFailure).isTrue
            release.send(Unit)
        }

        jobs.awaitAll()
    }

    @Test
    fun `CappedBackendExecutor caps beneath an unlimited outer`() = runTest {
        val outer = LimitedBackendExecutor(maxConcurrency = 10)
        val capped = outer.capped(maxConcurrency = 2)
        val entered = Channel<Unit>(Channel.UNLIMITED)
        val release = CompletableDeferred<Unit>()

        val jobs = (1..5).map {
            async {
                capped.withPermit {
                    entered.send(Unit)
                    release.await()
                }
            }
        }

        runCurrent()

        assertThat(entered.tryReceive().isSuccess).isTrue
        assertThat(entered.tryReceive().isSuccess).isTrue
        assertThat(entered.tryReceive().isFailure).isTrue

        release.complete(Unit)
        jobs.awaitAll()
    }

    @Test
    fun `CappedBackendExecutor respects the stricter of outer and inner limits`() = runTest {
        val outer = LimitedBackendExecutor(maxConcurrency = 1)
        val capped = outer.capped(maxConcurrency = 5)
        val entered = Channel<Unit>(Channel.UNLIMITED)
        val release = Channel<Unit>(Channel.UNLIMITED)

        val jobs = (1..4).map {
            async {
                capped.withPermit {
                    entered.send(Unit)
                    release.receive()
                }
            }
        }

        repeat(4) {
            runCurrent()
            assertThat(entered.tryReceive().isSuccess).isTrue
            assertThat(entered.tryReceive().isFailure).isTrue
            release.send(Unit)
        }

        jobs.awaitAll()
    }

    @Test
    suspend fun `withPermit returns the block's value`() {
        val executor = LimitedBackendExecutor(maxConcurrency = 1)
        val value = executor.withPermit { 42 }
        assertThat(value).isEqualTo(42)
    }
}
