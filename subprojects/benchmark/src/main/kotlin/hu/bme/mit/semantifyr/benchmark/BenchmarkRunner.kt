/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.benchmark

import hu.bme.mit.semantifyr.logging.error
import hu.bme.mit.semantifyr.logging.info
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import org.slf4j.LoggerFactory
import java.io.File
import java.lang.reflect.ParameterizedType
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.system.exitProcess

private val logger = LoggerFactory.getLogger("hu.bme.mit.semantifyr.benchmark.BenchmarkRunner")

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
    benchmark.setUp()
    benchmark.analyze(benchmark.measure())
}

// the only place the result's type is read from a class, since a written file names no type
@Suppress("UNCHECKED_CAST")
private fun <R> analyzeFile(benchmark: Benchmark<R>, file: Path) {
    val superType = benchmark.javaClass.genericSuperclass as ParameterizedType
    val resultSerializer = serializer(superType.actualTypeArguments.single()) as KSerializer<R>
    benchmark.analyze(benchmarkJson.decodeFromString(BenchmarkRun.serializer(resultSerializer), file.readText()))
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
