/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.benchmark

import hu.bme.mit.semantifyr.logging.error
import hu.bme.mit.semantifyr.logging.info
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.serializer
import org.slf4j.LoggerFactory
import java.io.File
import java.lang.reflect.ParameterizedType
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createParentDirectories
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.system.exitProcess

private val logger = LoggerFactory.getLogger("hu.bme.mit.semantifyr.benchmark.BenchmarkRunner")

internal object BenchmarkEnvironment {
    lateinit var runexec: Runexec

    private lateinit var runDirectory: Path

    private var repetitions = 0

    fun start(runexec: Runexec, runDirectory: Path) {
        this.runexec = runexec
        this.runDirectory = runDirectory
        repetitions = 0
    }

    fun nextDirectory(): Path {
        repetitions += 1
        return runDirectory.resolve("repetition-$repetitions").createDirectories()
    }
}

fun main(arguments: Array<String>) {
    val name = arguments.first()
    val benchmarkClass = findClass(name)
    if (benchmarkClass == null) {
        logger.error { "No benchmark is named $name" }
        exitProcess(1)
    }
    val benchmark = benchmarkClass.getDeclaredConstructor().newInstance() as Benchmark<*>
    val files = arguments.drop(1)
    try {
        if (files.isEmpty()) {
            measureAndAnalyze(benchmark)
        } else {
            for (file in files) {
                logger.info { "Analysis of $file" }
                analyzeFile(benchmark, Path(file))
            }
        }
    } catch (exception: RunexecUnavailableException) {
        logger.error { exception.message.orEmpty() }
        exitProcess(1)
    }
    exitProcess(0)
}

private fun <R> measureAndAnalyze(benchmark: Benchmark<R>) {
    val runexec = System.getProperty("benchmark.runexec") ?: "runexec"
    val outputDirectory = Path(System.getProperty("benchmark.output") ?: "build/benchmark")
    benchmark.analyze(measureAndWrite(benchmark, runexec, outputDirectory))
}

internal fun <R> measureAndWrite(
    benchmark: Benchmark<R>,
    runexecExecutable: String,
    outputDirectory: Path,
): BenchmarkRun<R> {
    val runexec = Runexec.probe(runexecExecutable)
    val name = benchmark.javaClass.simpleName
    val startedAt = Instant.now().toString()
    val runDirectory = outputDirectory.resolve(name).resolve(startedAt.replace(':', '-'))
    BenchmarkEnvironment.start(runexec, runDirectory)
    benchmark.setUp()
    val run = benchmark.measure()
    run.benchmark = name
    run.startedAt = startedAt
    val runexecSource = object : ContextSource<String>("runexec", String.serializer()) {
        override fun record(): String {
            return runexec.version
        }
    }
    for (source in listOf(CodeRevision, Host, Jvm, runexecSource)) {
        run.record(source)
    }
    val file = runDirectory.resolveSibling("${runDirectory.fileName}.json").createParentDirectories()
    writeAtomically(file, benchmarkJson.encodeToString(BenchmarkRun.serializer(resultSerializer(benchmark)), run))
    val loads = run.repetitions.map {
        it.hostState.loadBefore.oneMinute
    }
    logger.info { "Wrote $file, load average ${loads.minOrNull()} to ${loads.maxOrNull()} before the repetitions" }
    return run
}

private fun <R> analyzeFile(benchmark: Benchmark<R>, file: Path) {
    benchmark.analyze(benchmarkJson.decodeFromString(BenchmarkRun.serializer(resultSerializer(benchmark)), file.readText()))
}

// a written file names no type, so the result's type is read from the benchmark's class
@Suppress("UNCHECKED_CAST")
private fun <R> resultSerializer(benchmark: Benchmark<R>): KSerializer<R> {
    val superType = benchmark.javaClass.genericSuperclass as ParameterizedType
    return serializer(superType.actualTypeArguments.single()) as KSerializer<R>
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
