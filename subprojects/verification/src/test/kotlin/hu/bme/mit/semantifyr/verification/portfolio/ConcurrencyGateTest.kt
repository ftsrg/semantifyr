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
class ConcurrencyGateTest {

    @Test
    fun `LimitedConcurrencyGate runs up to maxConcurrency in parallel`() = runTest {
        val gate = LimitedConcurrencyGate(maxConcurrency = 2)
        val entered = Channel<Unit>(Channel.UNLIMITED)
        val release = CompletableDeferred<Unit>()

        val jobs = (1..5).map {
            async {
                gate.withPermit {
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
    fun `LimitedConcurrencyGate serializes with maxConcurrency 1`() = runTest {
        val gate = LimitedConcurrencyGate(maxConcurrency = 1)
        val entered = Channel<Unit>(Channel.UNLIMITED)
        val release = Channel<Unit>(Channel.UNLIMITED)

        val jobs = (1..4).map {
            async {
                gate.withPermit {
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
        val gate = LimitedConcurrencyGate(maxConcurrency = 1)
        val value = gate.withPermit { 42 }
        assertThat(value).isEqualTo(42)
    }
}
