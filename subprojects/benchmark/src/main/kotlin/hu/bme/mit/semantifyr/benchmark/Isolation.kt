/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.benchmark

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.lang.management.ManagementFactory
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.system.exitProcess

private const val CALLS_FILE = "calls.json"

private const val FAILURE_FILE = "failure.json"

internal class Outcome<R>(
    val jvm: ProcessObservation?,
    val calls: List<Call<R>>,
    val failure: Failure?,
)

@Serializable
sealed class Isolation {
    internal abstract fun <R> repeat(
        source: String,
        benchmark: Benchmark<*, R>,
        configuration: String,
        directory: Path,
        runexec: Runexec,
    ): Outcome<R>
}

@Serializable
@SerialName("runner-jvm")
data object RunnerJvm : Isolation() {
    override fun <R> repeat(
        source: String,
        benchmark: Benchmark<*, R>,
        configuration: String,
        directory: Path,
        runexec: Runexec,
    ): Outcome<R> {
        val trial = try {
            benchmark.setUp(configuration)
        } catch (throwable: Throwable) {
            return Outcome(null, emptyList(), Failure(Stage.Setup, throwable.stackTraceToString()))
        }
        val measurement = Measurement(runexec, benchmark.timeLimit, directory)
        try {
            return Outcome(null, listOf(measurement.call(trial, false)), null)
        } catch (throwable: Throwable) {
            return Outcome(null, emptyList(), measurement.failure(throwable))
        }
    }
}

@Serializable
@SerialName("fresh-jvm")
data class FreshJvm(
    val warmUpCalls: Int = 0,
) : Isolation() {
    override fun <R> repeat(
        source: String,
        benchmark: Benchmark<*, R>,
        configuration: String,
        directory: Path,
        runexec: Runexec,
    ): Outcome<R> {
        val java = Path(System.getProperty("java.home"), "bin", "java").toString()
        val arguments = listOf(
            "-cp",
            System.getProperty("java.class.path"),
            FreshJvm::class.java.name,
            source,
            configuration,
            warmUpCalls.toString(),
            directory.toString(),
            runexec.executable,
            runexec.version,
        )
        val command = listOf(java) + ManagementFactory.getRuntimeMXBean().inputArguments + arguments
        // in the task's working directory, where a benchmark's relative paths point
        val jvm = try {
            runexec.run(command, Path("").toAbsolutePath(), directory.resolve("jvm.out"), directory.resolve("jvm.err"), benchmark.timeLimit)
        } catch (exception: MeasurementFailedException) {
            return Outcome(null, emptyList(), Failure(Stage.Measurement, exception.stackTraceToString()))
        }
        val callsFile = directory.resolve(CALLS_FILE)
        val failureFile = directory.resolve(FAILURE_FILE)
        val calls = if (callsFile.exists()) {
            benchmarkJson.decodeFromString(benchmark.callsSerializer, callsFile.readText())
        } else {
            emptyList()
        }
        val failure = if (failureFile.exists()) {
            benchmarkJson.decodeFromString(Failure.serializer(), failureFile.readText())
        } else {
            null
        }
        return Outcome(jvm, calls, failure)
    }

    companion object {
        @JvmStatic
        fun main(arguments: Array<String>) {
            val (source, configuration, warmUpCalls, directory, runexecExecutable) = arguments
            val runexec = Runexec(runexecExecutable, arguments[5])
            val failure = try {
                callRepeatedly(instantiate(source).benchmark, configuration, warmUpCalls.toInt(), Path(directory), runexec)
            } catch (throwable: Throwable) {
                Failure(Stage.Setup, throwable.stackTraceToString())
            }
            if (failure != null) {
                writeAtomically(Path(directory).resolve(FAILURE_FILE), benchmarkJson.encodeToString(Failure.serializer(), failure))
            }
            exitProcess(0)
        }

        private fun <R> callRepeatedly(
            benchmark: Benchmark<*, R>,
            configuration: String,
            warmUpCalls: Int,
            directory: Path,
            runexec: Runexec,
        ): Failure? {
            val trial = benchmark.setUp(configuration)
            val calls = mutableListOf<Call<R>>()
            for (index in 0..warmUpCalls) {
                val measurement = Measurement(runexec, null, directory.resolve("call-${index + 1}").createDirectories())
                try {
                    calls += measurement.call(trial, index < warmUpCalls)
                } catch (throwable: Throwable) {
                    return measurement.failure(throwable)
                }
                writeAtomically(directory.resolve(CALLS_FILE), benchmarkJson.encodeToString(benchmark.callsSerializer, calls))
            }
            return null
        }
    }
}

internal fun instantiate(className: String): BenchmarkSource {
    return Class.forName(className).getDeclaredConstructor().newInstance() as BenchmarkSource
}
