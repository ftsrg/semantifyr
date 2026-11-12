/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.server

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class ContainerMetricsTest {

    private fun cgroup(directory: Path, files: Map<String, String>): Path {
        Files.createDirectories(directory)
        for ((name, content) in files) {
            Files.writeString(directory.resolve(name), content)
        }
        return directory
    }

    @Test
    fun `the meters read the cgroup's CPU, memory and process files`(@TempDir directory: Path) {
        val cgroupDirectory = cgroup(
            directory,
            mapOf(
                "cpu.stat" to "usage_usec 2500000\nuser_usec 2000000\nthrottled_usec 500000\n",
                "cpu.max" to "200000 100000\n",
                "memory.current" to "1000\n",
                "memory.stat" to "anon 700\ninactive_file 200\n",
                "memory.max" to "4096\n",
                "memory.swap.current" to "0\n",
                "memory.events" to "low 0\nmax 3\noom 1\noom_kill 1\n",
                "pids.current" to "5\n",
                "pids.max" to "max\n",
            ),
        )
        val registry = SimpleMeterRegistry()

        val metrics = ContainerMetrics(cgroupDirectory)
        metrics.bindTo(registry)

        val gauge = { name: String ->
            registry.get(name).gauge().value()
        }
        assertThat(registry.get("semantifyr.live.container.cpu.usage").functionCounter().count()).isEqualTo(2.5)
        assertThat(registry.get("semantifyr.live.container.cpu.throttled").functionCounter().count()).isEqualTo(0.5)
        assertThat(gauge("semantifyr.live.container.cpu.limit")).isEqualTo(2.0)
        assertThat(gauge("semantifyr.live.container.memory.working.set")).isEqualTo(800.0)
        assertThat(gauge("semantifyr.live.container.memory.limit")).isEqualTo(4096.0)
        assertThat(registry.get("semantifyr.live.container.memory.oom.kills").functionCounter().count()).isEqualTo(1.0)
        assertThat(gauge("semantifyr.live.container.processes")).isEqualTo(5.0)
        assertThat(gauge("semantifyr.live.container.processes.limit")).isEqualTo(Double.POSITIVE_INFINITY)
    }

    @Test
    fun `an unlimited CPU quota reports the processors the JVM sees`(@TempDir directory: Path) {
        val cgroupDirectory = cgroup(
            directory,
            mapOf(
                "cpu.stat" to "usage_usec 0\n",
                "cpu.max" to "max 100000\n",
            ),
        )
        val registry = SimpleMeterRegistry()

        val metrics = ContainerMetrics(cgroupDirectory)
        metrics.bindTo(registry)

        val limit = registry.get("semantifyr.live.container.cpu.limit").gauge().value()
        assertThat(limit).isEqualTo(Runtime.getRuntime().availableProcessors().toDouble())
    }

    @Test
    fun `the process's cgroup is found through its unified hierarchy entry`(@TempDir directory: Path) {
        val processCgroups = Files.writeString(directory.resolve("cgroup"), "0::/system.slice/live.scope\n")
        val root = directory.resolve("root")
        cgroup(
            root.resolve("system.slice/live.scope"),
            mapOf(
                "cpu.stat" to "usage_usec 1000000\n",
            ),
        )
        val registry = SimpleMeterRegistry()

        val metrics = ContainerMetrics.ofCurrentProcess(processCgroups, root)!!
        metrics.bindTo(registry)

        assertThat(registry.get("semantifyr.live.container.cpu.usage").functionCounter().count()).isEqualTo(1.0)
    }

    @Test
    fun `without a unified cgroup no meters are bound`(@TempDir directory: Path) {
        val processCgroups = Files.writeString(directory.resolve("cgroup"), "1:name=systemd:/\n")

        assertThat(ContainerMetrics.ofCurrentProcess(processCgroups, directory)).isNull()
    }
}
