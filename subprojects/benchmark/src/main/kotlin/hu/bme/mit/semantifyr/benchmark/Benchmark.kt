/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.benchmark

import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.nio.file.Path
import kotlin.time.Duration
import kotlin.time.TimeSource

sealed class Benchmark<C, R> {
    val name: String = javaClass.simpleName

    abstract val configurations: Map<String, C>

    open val repetitions = 5

    open val timeLimit: Duration? = null

    open fun setUp(configuration: C) {
    }

    open fun backendVersions(): Map<String, String> {
        return emptyMap()
    }

    @Suppress("UNCHECKED_CAST")
    internal val configurationSerializer by lazy {
        serializer(boundTypeArguments().getValue(Benchmark::class.java.typeParameters[0])) as KSerializer<C>
    }

    @Suppress("UNCHECKED_CAST")
    internal val resultSerializer by lazy {
        serializer(boundTypeArguments().getValue(Benchmark::class.java.typeParameters[1])) as KSerializer<R>
    }

    // a module's base may bind a type argument, so the bindings are followed from the class up to Benchmark
    private fun boundTypeArguments(): Map<Type, Type> {
        val bindings = mutableMapOf<Type, Type>()
        var current: Class<*> = javaClass
        while (current != Benchmark::class.java) {
            val superType = current.genericSuperclass
            if (superType is ParameterizedType) {
                val parameters = (superType.rawType as Class<*>).typeParameters
                parameters.zip(superType.actualTypeArguments).forEach { (parameter, argument) ->
                    bindings[parameter] = bindings[argument] ?: argument
                }
            }
            current = current.superclass
        }
        return bindings
    }
}

abstract class InJvmBenchmark<C, R> : Benchmark<C, R>() {
    open val warmUpCalls = 0

    abstract fun call(spans: Spans): R
}

abstract class ProcessBenchmark<C, R> : Benchmark<C, R>() {
    abstract fun repetition(processes: MeasuredProcesses): R
}

class Spans {
    internal val observations = mutableListOf<SpanObservation>()

    fun <T> measure(name: String, block: () -> T): T {
        val mark = TimeSource.Monotonic.markNow()
        val value = block()
        observations += SpanObservation(name, mark.elapsedNow().inWholeNanoseconds)
        return value
    }
}

class MeasuredProcesses internal constructor(
    private val runexec: Runexec,
    private val timeLimit: Duration?,
    val directory: Path,
) {
    internal val observations = mutableListOf<ProcessObservation>()

    private var deadline: TimeSource.Monotonic.ValueTimeMark? = null

    internal fun startClock() {
        deadline = timeLimit?.let {
            TimeSource.Monotonic.markNow() + it
        }
    }

    fun run(
        command: List<String>,
        workingDirectory: Path,
        outputFile: Path,
        errorFile: Path,
    ): ProcessObservation {
        val timeLeft = deadline?.let {
            -it.elapsedNow()
        }
        val observation = runexec.run(command, workingDirectory, outputFile, errorFile, timeLeft)
        observations += observation
        return observation
    }
}
