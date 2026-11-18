/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.benchmark

import hu.bme.mit.semantifyr.logging.error
import hu.bme.mit.semantifyr.logging.info
import kotlinx.serialization.builtins.ListSerializer
import org.slf4j.LoggerFactory
import java.lang.management.ManagementFactory
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createParentDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.system.exitProcess

private const val CALLS_FILE = "calls.json"

private const val FAILURE_FILE = "failure.json"

private val logger = LoggerFactory.getLogger(BenchmarkRunner::class.java)

fun main(arguments: Array<String>) {
    val name = arguments.single()
    val benchmarkClass = findClass(name)
    if (benchmarkClass == null) {
        logger.error { "No benchmark is named $name" }
        exitProcess(1)
    }
    val runexec = System.getProperty("benchmark.runexec") ?: "runexec"
    val outputDirectory = Path(System.getProperty("benchmark.output") ?: "build/benchmark")
    try {
        val file = runBenchmark(instantiate(benchmarkClass.name), runexec, outputDirectory)
        logger.info { "Wrote $file" }
    } catch (exception: RunexecUnavailableException) {
        logger.error { exception.message.orEmpty() }
        exitProcess(1)
    }
    exitProcess(0)
}

fun runBenchmark(
    benchmark: Benchmark<*, *>,
    runexecExecutable: String,
    outputDirectory: Path,
): Path {
    require(benchmark.repetitions >= 5) {
        "a benchmark repeats at least five times"
    }
    val runexec = Runexec.probe(runexecExecutable)
    val context = RunContext.record(benchmark.backendVersions(), runexec)
    return BenchmarkRunner(benchmark, runexec, context, outputDirectory).run()
}

internal data class ScheduledRepetition(
    val round: Int,
    val configuration: String,
)

internal fun interleavedRounds(configurations: List<String>, repetitions: Int): List<ScheduledRepetition> {
    return (0..<repetitions).flatMap { round ->
        configurations.map {
            ScheduledRepetition(round, it)
        }
    }
}

private class CallsReport<R>(
    val calls: List<Call<R>>,
    val failure: Failure?,
)

private class BenchmarkRunner<C, R>(
    private val benchmark: Benchmark<C, R>,
    private val runexec: Runexec,
    private val context: RunContext,
    outputDirectory: Path,
) {
    private val stamp = context.startedAt.replace(':', '-')

    private val runDirectory = outputDirectory.resolve(benchmark.name).resolve(stamp)

    private val file = outputDirectory.resolve(benchmark.name).resolve("$stamp.json").createParentDirectories()

    private val statement = Statement(
        benchmark = benchmark.name,
        kind = when (benchmark) {
            is InJvmBenchmark<C, R> -> {
                BenchmarkKind.InJvm
            }
            is ProcessBenchmark<C, R> -> {
                BenchmarkKind.Process
            }
        },
        configurations = benchmark.configurations,
        repetitions = benchmark.repetitions,
        warmUpCalls = (benchmark as? InJvmBenchmark<C, R>)?.warmUpCalls,
        timeLimit = benchmark.timeLimit,
    )

    fun run(): Path {
        val scheduled = interleavedRounds(benchmark.configurations.keys.toList(), benchmark.repetitions)
        val repetitions = mutableListOf<Repetition<R>>()
        for ((index, repetition) in scheduled.withIndex()) {
            logger.info { "Repetition ${index + 1} of ${scheduled.size}: round ${repetition.round + 1}, ${repetition.configuration}" }
            repetitions += repeat(repetition)
            writeBenchmarkRun(file, BenchmarkRun(statement, context, repetitions), benchmark.configurationSerializer, benchmark.resultSerializer)
        }
        return file
    }

    private fun repeat(repetition: ScheduledRepetition): Repetition<R> {
        val directory = runDirectory.resolve("round-${repetition.round + 1}").resolve(repetition.configuration).createDirectories()
        val loadBefore = LoadAverage.now()
        return when (benchmark) {
            is InJvmBenchmark<C, R> -> {
                inJvmRepetition(repetition, directory, loadBefore)
            }
            is ProcessBenchmark<C, R> -> {
                processRepetition(repetition, directory, loadBefore)
            }
        }
    }

    private fun inJvmRepetition(
        repetition: ScheduledRepetition,
        directory: Path,
        loadBefore: LoadAverage,
    ): Repetition<R> {
        val processes = MeasuredProcesses(runexec, benchmark.timeLimit, directory)
        val report = measure(processes) {
            freshJvm(repetition.configuration, processes)
        }
        return recorded(repetition, loadBefore, processes.observations, report)
    }

    @Suppress("UNCHECKED_CAST")
    private fun processRepetition(
        repetition: ScheduledRepetition,
        directory: Path,
        loadBefore: LoadAverage,
    ): Repetition<R> {
        val instance = try {
            (instantiate(benchmark.javaClass.name) as ProcessBenchmark<C, R>).also {
                it.setUpConfiguration(repetition.configuration)
            }
        } catch (throwable: Throwable) {
            val failure = Failure(Stage.Setup, throwable.stackTraceToString())
            return recorded(repetition, loadBefore, emptyList(), CallsReport(emptyList(), failure))
        }
        val processes = MeasuredProcesses(runexec, benchmark.timeLimit, directory)
        val report = measure(processes) {
            try {
                CallsReport(listOf(Call(false, emptyList(), instance.repetition(processes))), null)
            } catch (throwable: Throwable) {
                CallsReport(emptyList(), Failure(Stage.Call, throwable.stackTraceToString()))
            }
        }
        return recorded(repetition, loadBefore, processes.observations, report)
    }

    private fun recorded(
        repetition: ScheduledRepetition,
        loadBefore: LoadAverage,
        processes: List<ProcessObservation>,
        report: CallsReport<R>,
    ): Repetition<R> {
        return Repetition(
            configuration = repetition.configuration,
            round = repetition.round,
            hostState = HostState(loadBefore, LoadAverage.now()),
            processes = processes.toList(),
            calls = report.calls,
            failure = report.failure,
        )
    }

    private fun measure(processes: MeasuredProcesses, block: () -> CallsReport<R>): CallsReport<R> {
        val report = try {
            block()
        } catch (exception: MeasurementFailedException) {
            CallsReport(emptyList(), null)
        }
        val measurementFailure = processes.measurementFailure ?: return report
        return CallsReport(emptyList(), Failure(Stage.Measurement, measurementFailure.stackTraceToString()))
    }

    private fun freshJvm(configuration: String, processes: MeasuredProcesses): CallsReport<R> {
        val java = Path(System.getProperty("java.home"), "bin", "java").toString()
        val repetitionArguments = listOf(
            "-cp",
            System.getProperty("java.class.path"),
            FreshJvmRepetition::class.java.name,
            benchmark.javaClass.name,
            configuration,
            processes.directory.toString(),
        )
        val command = listOf(java) + ManagementFactory.getRuntimeMXBean().inputArguments + repetitionArguments
        // in the task's working directory, where a benchmark's relative paths point
        processes.run(command, Path("").toAbsolutePath(), processes.directory.resolve("jvm.out"), processes.directory.resolve("jvm.err"))
        val callsFile = processes.directory.resolve(CALLS_FILE)
        val failureFile = processes.directory.resolve(FAILURE_FILE)
        val calls = if (callsFile.exists()) {
            benchmarkJson.decodeFromString(ListSerializer(Call.serializer(benchmark.resultSerializer)), callsFile.readText())
        } else {
            emptyList()
        }
        val failure = if (failureFile.exists()) {
            benchmarkJson.decodeFromString(Failure.serializer(), failureFile.readText())
        } else {
            null
        }
        return CallsReport(calls, failure)
    }
}

object FreshJvmRepetition {
    @JvmStatic
    fun main(arguments: Array<String>) {
        val (className, configuration, directory) = arguments
        var stage = Stage.Setup
        try {
            val benchmark = instantiate(className) as InJvmBenchmark<*, *>
            benchmark.setUpConfiguration(configuration)
            stage = Stage.Call
            benchmark.callRepeatedly(Path(directory).resolve(CALLS_FILE))
        } catch (throwable: Throwable) {
            val failure = Failure(stage, throwable.stackTraceToString())
            writeAtomically(Path(directory).resolve(FAILURE_FILE), benchmarkJson.encodeToString(Failure.serializer(), failure))
        }
        exitProcess(0)
    }

    private fun <R> InJvmBenchmark<*, R>.callRepeatedly(callsFile: Path) {
        val calls = mutableListOf<Call<R>>()
        for (index in 0..warmUpCalls) {
            val spans = Spans()
            val result = call(spans)
            calls += Call(index < warmUpCalls, spans.observations.toList(), result)
            writeAtomically(callsFile, benchmarkJson.encodeToString(ListSerializer(Call.serializer(resultSerializer)), calls))
        }
    }
}

private fun <C> Benchmark<C, *>.setUpConfiguration(configuration: String) {
    setUp(configurations.getValue(configuration))
}

private fun instantiate(className: String): Benchmark<*, *> {
    return Class.forName(className).getDeclaredConstructor().newInstance() as Benchmark<*, *>
}
