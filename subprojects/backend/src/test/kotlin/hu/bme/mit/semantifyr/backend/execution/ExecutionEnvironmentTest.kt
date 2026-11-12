/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backend.execution

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

class ExecutionEnvironmentTest {

    private class CountingExecutor(
        private val available: Boolean,
    ) : BackendExecutor {
        val probes = AtomicInteger()

        override fun isAvailable(): Boolean {
            probes.incrementAndGet()
            return available
        }
    }

    @Test
    fun `an available executor is created and probed once`() {
        val created = AtomicInteger()
        val executor = CountingExecutor(available = true)
        val key = ExecutorKey("counting") {
            created.incrementAndGet()
            executor
        }
        val environment = ExecutionEnvironment.builder().build()

        repeat(3) {
            assertThat(environment.availability(key)).isEqualTo(AvailabilityReport.Available)
            assertThat(environment[key]).isSameAs(executor)
        }

        assertThat(created.get()).isEqualTo(1)
        assertThat(executor.probes.get()).isEqualTo(1)
    }

    @Test
    fun `an unavailable executor is probed once`() {
        val executor = CountingExecutor(available = false)
        val key = ExecutorKey("counting") {
            executor
        }
        val environment = ExecutionEnvironment.builder().build()

        repeat(3) {
            assertThat(environment.availability(key)).isInstanceOf(AvailabilityReport.Unavailable::class.java)
        }

        assertThat(executor.probes.get()).isEqualTo(1)
    }

    @Test
    fun `a factory that fails is asked once and its report repeats`() {
        val attempts = AtomicInteger()
        val key = ExecutorKey<BackendExecutor>("failing") {
            attempts.incrementAndGet()
            error("no executor")
        }
        val environment = ExecutionEnvironment.builder().build()

        val first = environment.availability(key)

        assertThat(first).isInstanceOf(AvailabilityReport.Unavailable::class.java)
        assertThat(environment.availability(key)).isSameAs(first)
        assertThat(attempts.get()).isEqualTo(1)
    }

    @Test
    fun `environments do not share executors`() {
        val created = AtomicInteger()
        val key = ExecutorKey("counting") {
            created.incrementAndGet()
            CountingExecutor(available = true)
        }

        ExecutionEnvironment.builder().build()[key]
        ExecutionEnvironment.builder().build()[key]

        assertThat(created.get()).isEqualTo(2)
    }
}
