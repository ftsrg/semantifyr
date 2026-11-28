/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.server

import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.MeterBinder
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.name
import kotlin.jvm.optionals.getOrDefault

private val backendCommands = mapOf(
    "spin" to "spin",
    "nuXmv" to "nuxmv",
    "verifyta" to "uppaal",
)

private const val THETA_COMMAND = "theta-xsts-cli"

private const val OTHER = "other"

private val backends = listOf(
    "theta",
    "spin",
    "nuxmv",
    "uppaal",
    OTHER,
)

class BackendProcessMetrics : MeterBinder {

    override fun bindTo(registry: MeterRegistry) {
        for (backend in backends) {
            Gauge.builder("semantifyr.live.backend.processes") {
                processesOf(backend).size
            }.tag("backend", backend).register(registry)
            Gauge.builder("semantifyr.live.backend.memory.resident") {
                processesOf(backend).sumOf {
                    residentBytes(it)
                }
            }.tag("backend", backend).baseUnit("bytes").register(registry)
        }
    }

    private fun processesOf(backend: String): List<ProcessHandle> {
        return ProcessHandle.current().children().filter {
            backendOf(it) == backend
        }.toList().flatMap {
            listOf(it) + it.descendants().toList()
        }
    }

    private fun backendOf(process: ProcessHandle): String {
        val info = process.info()
        if (THETA_COMMAND in info.commandLine().getOrDefault("")) {
            return "theta"
        }
        val command = info.command().getOrDefault("")
        val arguments = info.arguments().getOrDefault(emptyArray())
        val names = listOf(command) + arguments.take(1)
        return names.firstNotNullOfOrNull {
            backendCommands[Path.of(it).name]
        } ?: OTHER
    }

    private fun residentBytes(process: ProcessHandle): Long {
        val status = Path.of("/proc", process.pid().toString(), "status")
        val residentLine = try {
            Files.readAllLines(status).firstOrNull {
                it.startsWith("VmRSS:")
            }
        } catch (_: IOException) {
            null
        } ?: return 0
        return residentLine.split(Regex("\\s+"))[1].toLong() * 1024
    }
}
