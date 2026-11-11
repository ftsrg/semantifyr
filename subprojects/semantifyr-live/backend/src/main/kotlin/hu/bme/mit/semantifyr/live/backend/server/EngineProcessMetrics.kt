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

private val engineCommands = mapOf(
    "spin" to "spin",
    "nuXmv" to "nuxmv",
    "verifyta" to "uppaal",
)

private const val THETA_COMMAND = "theta-xsts-cli"

private const val OTHER = "other"

private val engines = listOf(
    "theta",
    "spin",
    "nuxmv",
    "uppaal",
    OTHER,
)

class EngineProcessMetrics : MeterBinder {

    override fun bindTo(registry: MeterRegistry) {
        for (engine in engines) {
            Gauge.builder("semantifyr.live.engine.processes") {
                processesOf(engine).size
            }.tag("engine", engine).register(registry)
            Gauge.builder("semantifyr.live.engine.memory.resident") {
                processesOf(engine).sumOf {
                    residentBytes(it)
                }
            }.tag("engine", engine).baseUnit("bytes").register(registry)
        }
    }

    private fun processesOf(engine: String): List<ProcessHandle> {
        return ProcessHandle.current().children().filter {
            engineOf(it) == engine
        }.toList().flatMap {
            listOf(it) + it.descendants().toList()
        }
    }

    private fun engineOf(process: ProcessHandle): String {
        val info = process.info()
        if (THETA_COMMAND in info.commandLine().getOrDefault("")) {
            return "theta"
        }
        val command = info.command().getOrDefault("")
        val arguments = info.arguments().getOrDefault(emptyArray())
        val names = listOf(command) + arguments.take(1)
        return names.firstNotNullOfOrNull {
            engineCommands[Path.of(it).name]
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
