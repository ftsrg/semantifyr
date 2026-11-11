/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.server

import com.sun.management.OperatingSystemMXBean
import io.micrometer.core.instrument.FunctionCounter
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.MeterBinder
import java.io.IOException
import java.lang.management.ManagementFactory
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists

private const val UNLIMITED = "max"

// the cgroup counts the engines' child processes too, which the JVM's own meters cannot see
class ContainerMetrics(
    private val cgroupDirectory: Path,
) : MeterBinder {

    override fun bindTo(registry: MeterRegistry) {
        FunctionCounter.builder("semantifyr.live.container.cpu.usage", this) {
            it.keyedValue("cpu.stat", "usage_usec") / 1_000_000.0
        }.baseUnit("seconds").register(registry)
        FunctionCounter.builder("semantifyr.live.container.cpu.throttled", this) {
            it.keyedValue("cpu.stat", "throttled_usec") / 1_000_000.0
        }.baseUnit("seconds").register(registry)
        Gauge.builder("semantifyr.live.container.cpu.limit") {
            cpuLimit()
        }.baseUnit("cores").register(registry)
        Gauge.builder("semantifyr.live.container.memory.working.set") {
            value("memory.current") - keyedValue("memory.stat", "inactive_file")
        }.baseUnit("bytes").register(registry)
        Gauge.builder("semantifyr.live.container.memory.limit") {
            memoryLimit()
        }.baseUnit("bytes").register(registry)
        Gauge.builder("semantifyr.live.container.memory.swap") {
            value("memory.swap.current")
        }.baseUnit("bytes").register(registry)
        FunctionCounter.builder("semantifyr.live.container.memory.oom.kills", this) {
            it.keyedValue("memory.events", "oom_kill")
        }.register(registry)
        Gauge.builder("semantifyr.live.container.processes") {
            value("pids.current")
        }.register(registry)
        Gauge.builder("semantifyr.live.container.processes.limit") {
            value("pids.max")
        }.register(registry)
    }

    private fun cpuLimit(): Double {
        val (quota, period) = read("cpu.max")?.split(' ') ?: return Double.NaN
        if (quota == UNLIMITED) {
            return Runtime.getRuntime().availableProcessors().toDouble()
        }
        return quota.toDouble() / period.toDouble()
    }

    private fun memoryLimit(): Double {
        val limit = read("memory.max") ?: return Double.NaN
        if (limit == UNLIMITED) {
            val system = ManagementFactory.getOperatingSystemMXBean() as OperatingSystemMXBean
            return system.totalMemorySize.toDouble()
        }
        return limit.toDouble()
    }

    private fun value(file: String): Double {
        val text = read(file) ?: return Double.NaN
        if (text == UNLIMITED) {
            return Double.POSITIVE_INFINITY
        }
        return text.toDouble()
    }

    private fun keyedValue(file: String, key: String): Double {
        val line = read(file)?.lineSequence()?.firstOrNull {
            it.startsWith("$key ")
        } ?: return Double.NaN
        return line.substringAfter(' ').toDouble()
    }

    private fun read(file: String): String? {
        return try {
            Files.readString(cgroupDirectory.resolve(file)).trim()
        } catch (_: IOException) {
            null
        }
    }

    companion object {
        fun ofCurrentProcess(
            processCgroups: Path = Path.of("/proc/self/cgroup"),
            cgroupRoot: Path = Path.of("/sys/fs/cgroup"),
        ): ContainerMetrics? {
            val lines = try {
                Files.readAllLines(processCgroups)
            } catch (_: IOException) {
                return null
            }
            val unifiedPath = lines.firstOrNull {
                it.startsWith("0::")
            }?.removePrefix("0::")?.removePrefix("/") ?: return null
            val directory = cgroupRoot.resolve(unifiedPath)
            if (!directory.resolve("cpu.stat").exists()) {
                return null
            }
            return ContainerMetrics(directory)
        }
    }
}
