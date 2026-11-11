/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.session

import hu.bme.mit.semantifyr.live.backend.BackendConfig
import hu.bme.mit.semantifyr.live.backend.VerificationConfig
import hu.bme.mit.semantifyr.live.backend.lsp.session.VerificationManager
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class VerificationManagerTest {

    private fun manager(concurrency: Int, registry: MeterRegistry = SimpleMeterRegistry()): VerificationManager {
        val config = BackendConfig(verification = VerificationConfig(concurrency = concurrency))
        return VerificationManager(config, registry)
    }

    private suspend fun <T> VerificationManager.withPermit(sessionId: String, block: suspend () -> T): T {
        return withPermit(enqueue(sessionId), block)
    }

    @Test
    fun `withPermit acquires and releases permit`() = runTest {
        val manager = manager(concurrency = 2)
        assertThat(manager.availablePermits).isEqualTo(2)
        manager.withPermit("session") {
            assertThat(manager.availablePermits).isEqualTo(1)
        }
        assertThat(manager.availablePermits).isEqualTo(2)
    }

    @Test
    fun `withPermit releases permit on exception`() = runTest {
        val manager = manager(concurrency = 1)
        try {
            manager.withPermit("session") {
                throw RuntimeException("boom")
            }
        } catch (_: RuntimeException) {
        }
        assertThat(manager.availablePermits).isEqualTo(1)
    }

    @Test
    fun `withPermit suspends when no permits available`() = runTest {
        val manager = manager(concurrency = 1)
        coroutineScope {
            var secondStarted = false
            val first = async {
                manager.withPermit("session") {
                    assertThat(secondStarted).isFalse()
                }
            }
            val second = async {
                manager.withPermit("session") {
                    secondStarted = true
                }
            }
            first.await()
            second.await()
            assertThat(secondStarted).isTrue()
            assertThat(manager.availablePermits).isEqualTo(1)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `a freed permit goes to the session that waited longest since it was last served`() = runTest {
        val manager = manager(concurrency = 1)
        val order = mutableListOf<String>()
        val release = CompletableDeferred<Unit>()
        val blocker = manager.enqueue("other")
        launch {
            manager.withPermit(blocker) {
                release.await()
            }
        }
        val requests = listOf(
            "busy-1",
            "busy-2",
            "busy-3",
            "busy-4",
            "single",
        ).associateWith {
            manager.enqueue(it.substringBefore("-"))
        }
        for ((name, request) in requests) {
            launch {
                manager.withPermit(request) {
                    order += name
                }
            }
        }
        runCurrent()
        assertThat(order).isEmpty()

        release.complete(Unit)
        advanceUntilIdle()
        assertThat(order).containsExactly(
            "busy-1",
            "single",
            "busy-2",
            "busy-3",
            "busy-4",
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `once saturated the waiting sessions take turns in the order they started waiting`() = runTest {
        val manager = manager(concurrency = 2)
        val order = mutableListOf<String>()
        val releases = mutableMapOf<String, CompletableDeferred<Unit>>()
        val names = listOf(
            "a1",
            "a2",
            "a3",
            "a4",
            "a5",
            "b1",
            "b2",
            "c1",
            "c2",
        )
        for (name in names) {
            val request = manager.enqueue(name.take(1))
            releases[name] = CompletableDeferred()
            launch {
                manager.withPermit(request) {
                    order += name
                    releases.getValue(name).await()
                }
            }
        }
        runCurrent()
        assertThat(order).containsExactly("a1", "a2")

        for (name in names) {
            releases.getValue(name).complete(Unit)
            runCurrent()
        }
        assertThat(order).containsExactly(
            "a1",
            "a2",
            "a3",
            "b1",
            "c1",
            "a4",
            "b2",
            "c2",
            "a5",
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `within a session the permits go in arrival order`() = runTest {
        val manager = manager(concurrency = 1)
        val order = mutableListOf<Int>()
        val requests = (1..4).map {
            manager.enqueue("session")
        }
        for ((index, request) in requests.withIndex().reversed()) {
            launch {
                manager.withPermit(request) {
                    order += index
                }
            }
        }
        advanceUntilIdle()
        assertThat(order).containsExactly(0, 1, 2, 3)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `an abandoned request leaves the queue and an abandoned grant passes the permit on`() = runTest {
        val manager = manager(concurrency = 1)
        val granted = manager.enqueue("first")
        val queued = manager.enqueue("second")
        val next = manager.enqueue("third")
        assertThat(manager.queuedCount).isEqualTo(2)

        manager.abandon(queued)
        assertThat(manager.queuedCount).isEqualTo(1)

        manager.abandon(granted)
        assertThat(manager.queuedCount).isEqualTo(0)
        var ran = false
        manager.withPermit(next) {
            ran = true
        }
        assertThat(ran).isTrue()
        assertThat(manager.availablePermits).isEqualTo(1)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `the queue and running gauges follow the permits`() = runTest {
        val registry = SimpleMeterRegistry()
        val manager = manager(concurrency = 1, registry = registry)
        val gauge = { name: String ->
            registry.get(name).gauge().value()
        }
        assertThat(gauge("semantifyr.live.verifications.limit")).isEqualTo(1.0)

        val release = CompletableDeferred<Unit>()
        val first = launch {
            manager.withPermit("session") {
                release.await()
            }
        }
        val second = launch {
            manager.withPermit("session") {
                release.await()
            }
        }
        runCurrent()
        assertThat(gauge("semantifyr.live.verifications.running")).isEqualTo(1.0)
        assertThat(gauge("semantifyr.live.verifications.queued")).isEqualTo(1.0)

        second.cancel()
        runCurrent()
        assertThat(gauge("semantifyr.live.verifications.queued")).isEqualTo(0.0)

        release.complete(Unit)
        first.join()
        assertThat(gauge("semantifyr.live.verifications.running")).isEqualTo(0.0)
    }
}
