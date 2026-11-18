/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.benchmark

import hu.bme.mit.semantifyr.logging.error
import hu.bme.mit.semantifyr.logging.info
import kotlinx.serialization.builtins.serializer
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createParentDirectories
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.system.exitProcess

private val logger = LoggerFactory.getLogger(BenchmarkRunner::class.java)

fun main(arguments: Array<String>) {
    val name = arguments.first()
    val sourceClass = findClass(name)
    if (sourceClass == null) {
        logger.error { "No benchmark is named $name" }
        exitProcess(1)
    }
    val source = instantiate(sourceClass.name)
    val files = arguments.drop(1).map {
        Path(it)
    }.ifEmpty {
        val runexec = System.getProperty("benchmark.runexec") ?: "runexec"
        val outputDirectory = Path(System.getProperty("benchmark.output") ?: "build/benchmark")
        try {
            listOf(runBenchmark(source, runexec, outputDirectory))
        } catch (exception: RunexecUnavailableException) {
            logger.error { exception.message.orEmpty() }
            exitProcess(1)
        }
    }
    for (file in files) {
        logger.info { "Analysis of $file" }
        source.benchmark.analyze(file, logger)
    }
    exitProcess(0)
}

fun runBenchmark(
    source: BenchmarkSource,
    runexecExecutable: String,
    outputDirectory: Path,
): Path {
    val runexec = Runexec.probe(runexecExecutable)
    return BenchmarkRunner(source.javaClass.name, source.benchmark, runexec, outputDirectory).run()
}

private class BenchmarkRunner<C, R>(
    private val source: String,
    private val benchmark: Benchmark<C, R>,
    private val runexec: Runexec,
    outputDirectory: Path,
) {
    private val name = source.substringAfterLast('.')

    private val startedAt = Instant.now().toString()

    private val runDirectory = outputDirectory.resolve(name).resolve(startedAt.replace(':', '-'))

    private val file = runDirectory.resolveSibling("${runDirectory.fileName}.json").createParentDirectories()

    private val statement = Statement(name, benchmark.configurations, benchmark.isolation, benchmark.schedule, benchmark.timeLimit)

    private val runexecSource = object : ContextSource<String>("runexec", String.serializer()) {
        override fun record(): String {
            return runexec.version
        }
    }

    private val context = (listOf(CodeRevision, Host, Jvm, runexecSource) + benchmark.context).associate {
        it.name to it.recordAsJson()
    }

    fun run(): Path {
        val scheduled = benchmark.schedule.order(benchmark.configurations.keys.toList())
        val repetitions = mutableListOf<Repetition<R>>()
        for ((index, repetition) in scheduled.withIndex()) {
            logger.info { "Repetition ${index + 1} of ${scheduled.size}: round ${repetition.round + 1}, ${repetition.configuration}" }
            repetitions += repeat(repetition)
            writeAtomically(file, benchmarkJson.encodeToString(benchmark.runSerializer, BenchmarkRun(statement, startedAt, context, repetitions)))
        }
        val loads = repetitions.map {
            it.hostState.loadBefore.oneMinute
        }
        logger.info { "Wrote $file, load average ${loads.min()} to ${loads.max()} before the repetitions" }
        return file
    }

    private fun repeat(repetition: ScheduledRepetition): Repetition<R> {
        val directory = runDirectory.resolve("round-${repetition.round + 1}").resolve(repetition.configuration).createDirectories()
        val loadBefore = LoadAverage.now()
        val outcome = benchmark.isolation.repeat(source, benchmark, repetition.configuration, directory, runexec)
        return Repetition(
            configuration = repetition.configuration,
            round = repetition.round,
            hostState = HostState(loadBefore, LoadAverage.now()),
            jvm = outcome.jvm,
            calls = outcome.calls,
            failure = outcome.failure,
        )
    }
}

private fun findClass(simpleName: String): Class<*>? {
    val classNames = System.getProperty("java.class.path").split(File.pathSeparator).map {
        Path(it)
    }.filter {
        it.isDirectory()
    }.flatMap { root ->
        Files.walk(root).use { paths ->
            paths.filter {
                it.name == "$simpleName.class"
            }.map {
                root.relativize(it).joinToString(".").removeSuffix(".class")
            }.toList()
        }
    }
    require(classNames.size <= 1) {
        "several classes are named $simpleName"
    }
    return classNames.singleOrNull()?.let {
        Class.forName(it)
    }
}
