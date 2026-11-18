/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.benchmark

import kotlinx.serialization.Serializable
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.math.ceil
import kotlin.time.Duration

@Serializable
data class ProcessObservation(
    val startTime: String,
    val wallTimeSeconds: Double,
    val cpuTimeSeconds: Double,
    val peakMemoryBytes: Long,
    val terminationReason: String?,
    val exitCode: Int?,
    val exitSignal: Int?,
    val cpuPressureSeconds: Double?,
    val ioPressureSeconds: Double?,
    val memoryPressureSeconds: Double?,
) {
    companion object {
        fun parse(runexecOutput: String): ProcessObservation {
            val values = runexecOutput.lines().filter {
                '=' in it
            }.associate {
                it.substringBefore('=') to it.substringAfter('=')
            }
            val measured = listOf(
                "starttime",
                "walltime",
                "cputime",
                "memory",
            )
            if (!values.keys.containsAll(measured)) {
                throw MeasurementFailedException("runexec measured nothing and reported ${values.entries.joinToString()}")
            }
            return ProcessObservation(
                startTime = values.getValue("starttime"),
                wallTimeSeconds = values.getValue("walltime").removeSuffix("s").toDouble(),
                cpuTimeSeconds = values.getValue("cputime").removeSuffix("s").toDouble(),
                peakMemoryBytes = values.getValue("memory").removeSuffix("B").toLong(),
                terminationReason = values["terminationreason"],
                exitCode = values["returnvalue"]?.toInt(),
                exitSignal = values["exitsignal"]?.toInt(),
                cpuPressureSeconds = values["pressure-cpu-some"]?.removeSuffix("s")?.toDouble(),
                ioPressureSeconds = values["pressure-io-some"]?.removeSuffix("s")?.toDouble(),
                memoryPressureSeconds = values["pressure-memory-some"]?.removeSuffix("s")?.toDouble(),
            )
        }
    }
}

class RunexecUnavailableException(message: String) : Exception(message)

class MeasurementFailedException(message: String) : Exception(message)

class Runexec private constructor(
    private val executable: String,
    val version: String,
) {

    // runexec merges stderr into its output file behind a header of its own, so the tool writes its own files and runexec's sit beside them
    fun run(
        command: List<String>,
        workingDirectory: Path,
        outputFile: Path,
        errorFile: Path,
        timeLimit: Duration?,
    ): ProcessObservation {
        val limit = timeLimit?.let {
            listOf(
                "--walltimelimit",
                ceil(it.inWholeMilliseconds.coerceAtLeast(1) / 1000.0).toLong().toString(),
            )
        } ?: emptyList()
        val runexecCommand = listOf(
            executable,
            "--no-container",
            "--output",
            outputFile.resolveSibling("${outputFile.fileName}.runexec").absolutePathString(),
        ) + limit + listOf(
            "--",
            "sh",
            "-c",
            "output=\"$1\"; error=\"$2\"; shift 2; exec \"$@\" >> \"\$output\" 2>> \"\$error\"",
            "sh",
            outputFile.absolutePathString(),
            errorFile.absolutePathString(),
        ) + command
        val process = ProcessBuilder(runexecCommand)
            .directory(workingDirectory.toFile())
            .redirectError(runexecLog(outputFile).toFile())
            .start()
        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()
        if (exitCode != 0) {
            throw MeasurementFailedException("runexec exited with $exitCode as ${runexecLog(outputFile)} shows")
        }
        return ProcessObservation.parse(output)
    }

    private fun runexecLog(outputFile: Path): Path {
        return outputFile.resolveSibling("${outputFile.fileName}.runexec.log")
    }

    companion object {
        // an installed runexec may still lack the delegated cgroups it measures through, so the probe measures a command
        fun probe(executable: String): Runexec {
            val version = commandOutputOrNull(executable, "--version") ?: throw RunexecUnavailableException("runexec is missing at $executable")
            val directory = Files.createTempDirectory("runexec-probe")
            val runexec = Runexec(executable, version)
            try {
                runexec.run(listOf("true"), directory, directory.resolve("out"), directory.resolve("err"), null)
            } catch (exception: MeasurementFailedException) {
                throw RunexecUnavailableException("runexec cannot measure on this host as ${directory.resolve("out.runexec.log")} shows")
            }
            directory.toFile().deleteRecursively()
            return runexec
        }
    }
}

fun commandOutputOrNull(vararg command: String): String? {
    val process = try {
        ProcessBuilder(*command).redirectError(ProcessBuilder.Redirect.DISCARD).start()
    } catch (exception: IOException) {
        return null
    }
    val output = process.inputStream.bufferedReader().readText().trim()
    if (process.waitFor() != 0) {
        return null
    }
    return output
}
