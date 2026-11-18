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
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.serializer
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.core.config.Configurator
import org.slf4j.LoggerFactory
import java.io.File
import java.lang.reflect.ParameterizedType
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.jar.JarFile
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createParentDirectories
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.system.exitProcess

private val logger = LoggerFactory.getLogger("hu.bme.mit.semantifyr.benchmark.BenchmarkRunner")

internal class RunOutput(
    val benchmark: String,
    val startedAt: String,
    val file: Path,
    private val context: Map<String, JsonElement>,
    private val serializer: KSerializer<out BenchmarkRun<*>>,
) {
    @Suppress("UNCHECKED_CAST")
    fun write(run: BenchmarkRun<*>) {
        run.describe(benchmark, startedAt, context)
        writeAtomically(file, benchmarkJson.encodeToString(serializer as KSerializer<BenchmarkRun<*>>, run))
    }
}

internal object BenchmarkEnvironment {
    lateinit var runexec: Runexec
        private set

    private lateinit var directory: Path

    private var output: RunOutput? = null

    private var repetitions = 0

    fun start(
        runexec: Runexec,
        directory: Path,
        output: RunOutput?,
    ) {
        this.runexec = runexec
        this.directory = directory
        this.output = output
        repetitions = 0
    }

    fun nextDirectory(): Path {
        repetitions += 1
        return directory.resolve("repetition-$repetitions").createDirectories()
    }

    fun written(run: BenchmarkRun<*>) {
        output?.write(run)
    }
}

fun main(arguments: Array<String>) {
    val name = arguments.first()
    val benchmarkClass = findClass(name)
    if (benchmarkClass == null) {
        logger.error { "No benchmark is named $name" }
        exitProcess(1)
    }
    val sourceSets = System.getProperty("benchmark.sourceSets").orEmpty().split(File.pathSeparator).filter {
        it.isNotEmpty()
    }.map {
        Path(it)
    }
    logBenchmarkClassesAtInfo(sourceSets)
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
    val file = runDirectory.resolveSibling("${runDirectory.fileName}.json").createParentDirectories()
    val runexecSource = object : ContextSource<String>("runexec", String.serializer()) {
        override fun record(): String {
            return runexec.version
        }
    }
    val context = listOf(CodeRevision, Host, Jvm, runexecSource).associate {
        it.name to it.recordAsJson()
    }
    BenchmarkEnvironment.start(runexec, runDirectory, RunOutput(name, startedAt, file, context, BenchmarkRun.serializer(resultSerializer(benchmark))))
    logger.info { "Writing $file" }
    benchmark.setUp()
    val run = benchmark.measure()
    BenchmarkEnvironment.written(run)
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

internal fun logBenchmarkClassesAtInfo(sourceSets: List<Path>) {
    // a source set's classes directory for a language it has no sources in is never created
    val classNames = sourceSets.filter {
        it.exists()
    }.flatMap {
        classNames(it)
    }
    Configurator.setLevel(
        classNames.associateWith {
            Level.INFO
        },
    )
}

private fun classNames(sourceSet: Path): List<String> {
    if (sourceSet.isDirectory()) {
        return Files.walk(sourceSet).use { paths ->
            paths.filter {
                it.name.endsWith(".class")
            }.map {
                sourceSet.relativize(it).joinToString(".").removeSuffix(".class")
            }.toList()
        }
    }
    return JarFile(sourceSet.toFile()).use { jar ->
        jar.entries().asSequence().map {
            it.name
        }.filter {
            it.endsWith(".class")
        }.map {
            it.removeSuffix(".class").replace('/', '.')
        }.toList()
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
