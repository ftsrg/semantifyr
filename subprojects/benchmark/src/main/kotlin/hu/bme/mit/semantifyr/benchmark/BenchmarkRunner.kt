/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.benchmark

import hu.bme.mit.semantifyr.logging.error
import hu.bme.mit.semantifyr.logging.info
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles
import java.lang.management.ManagementFactory
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createParentDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.system.exitProcess

private const val REPETITION_ARGUMENT = "--repetition"

private val runnerClass = MethodHandles.lookup().lookupClass().name

private val logger = LoggerFactory.getLogger(runnerClass)

fun main(arguments: Array<String>) {
    if (arguments.first() == REPETITION_ARGUMENT) {
        runCalls(arguments[1], arguments[2], Path(arguments[3]))
        exitProcess(0)
    }
    val benchmarkClass = findClass(arguments.single())
    if (benchmarkClass == null) {
        logger.error { "No benchmark is named ${arguments.single()}" }
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

@Serializable
internal data class CallsReport<R>(
    val calls: List<Call<R>>,
    val failure: Failure?,
)

private class MeasuredReport<R>(
    val processes: MeasuredProcesses?,
    val report: CallsReport<R>,
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

    private val repetitions = mutableListOf<Repetition<R>>()

    fun run(): Path {
        val scheduled = interleavedRounds(benchmark.configurations.keys.toList(), benchmark.repetitions)
        for ((index, repetition) in scheduled.withIndex()) {
            logger.info { "Repetition ${index + 1} of ${scheduled.size}: round ${repetition.round + 1}, ${repetition.configuration}" }
            val directory = runDirectory.resolve("round-${repetition.round + 1}").resolve(repetition.configuration).createDirectories()
            val loadBefore = LoadAverage.now()
            val measured = when (benchmark) {
                is InJvmBenchmark<C, R> -> {
                    freshJvm(repetition.configuration, directory)
                }
                is ProcessBenchmark<C, R> -> {
                    processRepetition(repetition.configuration, directory)
                }
            }
            val measurementFailure = measured.processes?.measurementFailure
            repetitions += Repetition(
                configuration = repetition.configuration,
                round = repetition.round,
                hostState = HostState(loadBefore, LoadAverage.now()),
                processes = measured.processes?.observations.orEmpty(),
                calls = if (measurementFailure == null) {
                    measured.report.calls
                } else {
                    emptyList()
                },
                failure = if (measurementFailure == null) {
                    measured.report.failure
                } else {
                    Failure(Stage.Measurement, measurementFailure.stackTraceToString())
                },
            )
            writeBenchmarkRun(file, BenchmarkRun(statement, context, repetitions), benchmark.configurationSerializer, benchmark.resultSerializer)
        }
        return file
    }

    private fun freshJvm(configuration: String, directory: Path): MeasuredReport<R> {
        val reportFile = directory.resolve("calls.json")
        val java = Path(System.getProperty("java.home"), "bin", "java").toString()
        val repetitionArguments = listOf(
            "-cp",
            System.getProperty("java.class.path"),
            runnerClass,
            REPETITION_ARGUMENT,
            benchmark.javaClass.name,
            configuration,
            reportFile.toString(),
        )
        val command = listOf(java) + ManagementFactory.getRuntimeMXBean().inputArguments + repetitionArguments
        val processes = MeasuredProcesses(runexec, benchmark.timeLimit, directory)
        try {
            // in the task's working directory, where a benchmark's relative paths point
            processes.run(command, Path("").toAbsolutePath(), directory.resolve("jvm.out"), directory.resolve("jvm.err"))
        } catch (exception: MeasurementFailedException) {
            return MeasuredReport(processes, CallsReport(emptyList(), null))
        }
        if (!reportFile.exists()) {
            return MeasuredReport(processes, CallsReport(emptyList(), null))
        }
        val report = benchmarkJson.decodeFromString(CallsReport.serializer(benchmark.resultSerializer), reportFile.readText())
        return MeasuredReport(processes, report)
    }

    @Suppress("UNCHECKED_CAST")
    private fun processRepetition(configuration: String, directory: Path): MeasuredReport<R> {
        val instance = try {
            (instantiate(benchmark.javaClass.name) as ProcessBenchmark<C, R>).also {
                it.setUp(benchmark.configurations.getValue(configuration))
            }
        } catch (throwable: Throwable) {
            return MeasuredReport(null, CallsReport(emptyList(), Failure(Stage.Setup, throwable.stackTraceToString())))
        }
        val processes = MeasuredProcesses(runexec, benchmark.timeLimit, directory)
        val report = try {
            CallsReport(listOf(Call(false, emptyList(), instance.repetition(processes))), null)
        } catch (throwable: Throwable) {
            CallsReport(emptyList(), Failure(Stage.Call, throwable.stackTraceToString()))
        }
        return MeasuredReport(processes, report)
    }
}

private fun instantiate(className: String): Benchmark<*, *> {
    return Class.forName(className).getDeclaredConstructor().newInstance() as Benchmark<*, *>
}

private fun runCalls(
    className: String,
    configuration: String,
    reportFile: Path,
) {
    val benchmark = try {
        instantiate(className) as InJvmBenchmark<*, *>
    } catch (throwable: Throwable) {
        // without calls every result serializer writes the same report
        writeCallsReport(reportFile, CallsReport(emptyList(), Failure(Stage.Setup, throwable.stackTraceToString())), Unit.serializer())
        return
    }
    runCalls(benchmark, configuration, reportFile)
}

private fun <C, R> runCalls(
    benchmark: InJvmBenchmark<C, R>,
    configuration: String,
    reportFile: Path,
) {
    val calls = mutableListOf<Call<R>>()
    var stage = Stage.Setup
    try {
        benchmark.setUp(benchmark.configurations.getValue(configuration))
        stage = Stage.Call
        for (index in 0..benchmark.warmUpCalls) {
            val spans = Spans()
            val result = benchmark.call(spans)
            calls += Call(index < benchmark.warmUpCalls, spans.observations.toList(), result)
            writeCallsReport(reportFile, CallsReport(calls, null), benchmark.resultSerializer)
        }
    } catch (throwable: Throwable) {
        writeCallsReport(reportFile, CallsReport(calls, Failure(stage, throwable.stackTraceToString())), benchmark.resultSerializer)
    }
}

private fun <R> writeCallsReport(
    reportFile: Path,
    report: CallsReport<R>,
    resultSerializer: KSerializer<R>,
) {
    writeAtomically(reportFile, benchmarkJson.encodeToString(CallsReport.serializer(resultSerializer), report))
}
