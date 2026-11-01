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
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class VerificationManagerTest {

    private fun manager(concurrency: Int, registry: MeterRegistry = SimpleMeterRegistry()): VerificationManager {
        val config = BackendConfig(verification = VerificationConfig(concurrency = concurrency))
        return VerificationManager(config, registry)
    }

    @Test
    fun `withPermit acquires and releases permit`() = runTest {
        val manager = manager(concurrency = 2)
        assertThat(manager.availablePermits).isEqualTo(2)
        manager.withPermit {
            assertThat(manager.availablePermits).isEqualTo(1)
        }
        assertThat(manager.availablePermits).isEqualTo(2)
    }

    @Test
    fun `withPermit releases permit on exception`() = runTest {
        val manager = manager(concurrency = 1)
        try {
            manager.withPermit {
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
                manager.withPermit {
                    assertThat(secondStarted).isFalse()
                }
            }
            val second = async {
                manager.withPermit {
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
    fun `the queue and running gauges follow the permits`() = runTest {
        val registry = SimpleMeterRegistry()
        val manager = manager(concurrency = 1, registry = registry)
        val gauge = { name: String ->
            registry.get(name).gauge().value()
        }
        assertThat(gauge("semantifyr.live.verifications.limit")).isEqualTo(1.0)

        val release = CompletableDeferred<Unit>()
        val first = launch {
            manager.withPermit {
                release.await()
            }
        }
        val second = launch {
            manager.withPermit {
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
