/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.session

import hu.bme.mit.semantifyr.live.backend.BackendConfig
import hu.bme.mit.semantifyr.live.backend.VerificationConfig
import hu.bme.mit.semantifyr.live.backend.lsp.session.VerificationManager
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class VerificationManagerTest {

    private fun manager(concurrency: Int): VerificationManager {
        val config = BackendConfig(verification = VerificationConfig(concurrency = concurrency))
        return VerificationManager(config)
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
}
