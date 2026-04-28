/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.session

import hu.bme.mit.semantifyr.live.backend.BackendConfig
import hu.bme.mit.semantifyr.live.backend.VerificationConfig
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

class GlobalVerificationManagerTest {

    private fun manager(concurrency: Int): GlobalVerificationManager {
        val config = BackendConfig(
            verification = VerificationConfig(concurrency = concurrency, timeout = 300.seconds),
        )
        return GlobalVerificationManager(config)
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
                assertThat(manager.availablePermits).isEqualTo(0)
                throw RuntimeException("test error")
            }
        } catch (_: RuntimeException) {
            // expected
        }

        assertThat(manager.availablePermits).isEqualTo(1)
    }

    @Test
    fun `withPermit suspends when no permits available`() = runTest {
        val manager = manager(concurrency = 1)
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
