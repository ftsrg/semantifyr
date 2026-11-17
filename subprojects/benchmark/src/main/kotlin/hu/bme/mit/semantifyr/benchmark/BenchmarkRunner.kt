/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.benchmark

import kotlinx.serialization.Serializable
import java.lang.invoke.MethodHandles
import java.lang.management.ManagementFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createParentDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.system.exitProcess
import kotlin.time.TimeSource

private const val REPETITION_ARGUMENT = "--repetition"

private val runnerClass = MethodHandles.lookup().lookupClass().name

fun main(arguments: Array<String>) {
    if (arguments.first() == REPETITION_ARGUMENT) {
        runCalls(instantiate(arguments[1]) as InJvmBenchmark<*, *>, arguments[2], Path(arguments[3]))
        exitProcess(0)
    }
    val runexec = System.getProperty("benchmark.runexec") ?: "runexec"
    val outputDirectory = Path(System.getProperty("benchmark.output") ?: "build/benchmark")
    try {
        val benchmarkClass = requireNotNull(findClass(arguments.single())) {
            "no benchmark named ${arguments.single()} in the benchmark source set"
        }
        println(runBenchmark(instantiate(benchmarkClass.name), runexec, outputDirectory))
    } catch (exception: RunexecUnavailableException) {
        System.err.println("error: ${exception.message}")
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

// every configuration once per round, so all of them see the same drift of the host's load
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
            println("repetition ${index + 1} of ${scheduled.size}: round ${repetition.round + 1}, ${repetition.configuration}")
            val directory = runDirectory.resolve("round-${repetition.round + 1}").resolve(repetition.configuration).createDirectories()
            val processes = MeasuredProcesses(runexec, deadline(), directory)
            val loadBefore = LoadAverage.now()
            val report = when (benchmark) {
                is InJvmBenchmark<C, R> -> {
                    freshJvm(repetition.configuration, processes)
                }
                is ProcessBenchmark<C, R> -> {
                    processRepetition(repetition.configuration, processes)
                }
            }
            repetitions += Repetition(
                configuration = repetition.configuration,
                round = repetition.round,
                hostState = HostState(loadBefore, LoadAverage.now()),
                processes = processes.observations.toList(),
                calls = report.calls,
                failure = report.failure,
            )
            writeBenchmarkRun(file, BenchmarkRun(statement, context, repetitions), benchmark.configurationSerializer, benchmark.resultSerializer)
        }
        return file
    }

    private fun deadline(): TimeSource.Monotonic.ValueTimeMark? {
        return benchmark.timeLimit?.let {
            TimeSource.Monotonic.markNow() + it
        }
    }

    private fun freshJvm(configuration: String, processes: MeasuredProcesses): CallsReport<R> {
        val reportFile = processes.directory.resolve("calls.json")
        val command = listOf(ProcessHandle.current().info().command().orElseThrow()) + ManagementFactory.getRuntimeMXBean().inputArguments + listOf(
            "-cp",
            System.getProperty("java.class.path"),
            runnerClass,
            REPETITION_ARGUMENT,
            benchmark.javaClass.name,
            configuration,
            reportFile.toString(),
        )
        // the fresh JVM works where the benchmark task does, so a benchmark's relative paths name the same files
        processes.run(command, Path("").toAbsolutePath(), processes.directory.resolve("jvm.out"), processes.directory.resolve("jvm.err"))
        if (!reportFile.exists()) {
            return CallsReport(emptyList(), null)
        }
        return benchmarkJson.decodeFromString(CallsReport.serializer(benchmark.resultSerializer), reportFile.readText())
    }

    @Suppress("UNCHECKED_CAST")
    private fun processRepetition(configuration: String, processes: MeasuredProcesses): CallsReport<R> {
        val instance = instantiate(benchmark.javaClass.name) as ProcessBenchmark<C, R>
        try {
            instance.setUp(benchmark.configurations.getValue(configuration))
        } catch (exception: Exception) {
            return CallsReport(emptyList(), Failure(Stage.Setup, exception.stackTraceToString()))
        }
        try {
            return CallsReport(listOf(Call(false, emptyList(), instance.repetition(processes))), null)
        } catch (exception: Exception) {
            return CallsReport(emptyList(), Failure(Stage.Call, exception.stackTraceToString()))
        }
    }
}

private fun instantiate(className: String): Benchmark<*, *> {
    return Class.forName(className).getDeclaredConstructor().newInstance() as Benchmark<*, *>
}

private fun <C, R> runCalls(
    benchmark: InJvmBenchmark<C, R>,
    configuration: String,
    reportFile: Path,
) {
    val calls = mutableListOf<Call<R>>()
    val report = { failure: Failure? ->
        val temporaryFile = reportFile.resolveSibling("${reportFile.fileName}.writing")
        temporaryFile.writeText(benchmarkJson.encodeToString(CallsReport.serializer(benchmark.resultSerializer), CallsReport(calls, failure)))
        Files.move(temporaryFile, reportFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }
    try {
        benchmark.setUp(benchmark.configurations.getValue(configuration))
    } catch (exception: Exception) {
        report(Failure(Stage.Setup, exception.stackTraceToString()))
        return
    }
    for (index in 0..benchmark.warmUpCalls) {
        val spans = Spans()
        val result = try {
            benchmark.call(spans)
        } catch (exception: Exception) {
            report(Failure(Stage.Call, exception.stackTraceToString()))
            return
        }
        calls += Call(index < benchmark.warmUpCalls, spans.observations.toList(), result)
        report(null)
    }
}
