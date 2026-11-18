/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.benchmark

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.serializer
import java.lang.management.ManagementFactory
import java.lang.reflect.ParameterizedType
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.reflect.KClass
import kotlin.system.exitProcess
import kotlin.time.Duration

private const val CALLS_FILE = "calls.json"

private const val FAILURE_FILE = "failure.json"

interface FreshJvmRun<R> {
    fun run(measurement: Measurement): R
}

inline fun <reified I, reified R> freshJvm(
    run: KClass<out FreshJvmRun<R>>,
    input: I,
    timeLimit: Duration? = null,
): Measured<R> {
    return startFreshJvm(run.java.name, benchmarkJson.encodeToString(serializer<I>(), input), serializer<R>(), timeLimit)
}

@PublishedApi
internal fun <R> startFreshJvm(
    runClass: String,
    input: String,
    resultSerializer: KSerializer<R>,
    timeLimit: Duration?,
): Measured<R> {
    val directory = BenchmarkEnvironment.nextDirectory()
    val runexec = BenchmarkEnvironment.runexec
    val java = Path(System.getProperty("java.home"), "bin", "java").toString()
    val arguments = listOf(
        "-cp",
        System.getProperty("java.class.path"),
        FreshJvm::class.java.name,
        runClass,
        input,
        directory.toString(),
        runexec.executable,
        runexec.version,
    )
    val command = listOf(java) + ManagementFactory.getRuntimeMXBean().inputArguments + arguments
    val loadBefore = LoadAverage.now()
    // in the task's working directory, where a benchmark's relative paths point
    val jvm = try {
        runexec.run(command, Path("").toAbsolutePath(), directory.resolve("jvm.out"), directory.resolve("jvm.err"), timeLimit)
    } catch (exception: MeasurementFailedException) {
        return Measured(HostState(loadBefore, LoadAverage.now()), timeLimit, null, emptyList(), Failure(Stage.Measurement, exception.stackTraceToString()))
    }
    val callsFile = directory.resolve(CALLS_FILE)
    val failureFile = directory.resolve(FAILURE_FILE)
    val calls = if (callsFile.exists()) {
        benchmarkJson.decodeFromString(ListSerializer(Call.serializer(resultSerializer)), callsFile.readText())
    } else {
        emptyList()
    }
    val failure = if (failureFile.exists()) {
        benchmarkJson.decodeFromString(Failure.serializer(), failureFile.readText())
    } else {
        null
    }
    return Measured(HostState(loadBefore, LoadAverage.now()), timeLimit, jvm, calls, failure)
}

object FreshJvm {
    @JvmStatic
    fun main(arguments: Array<String>) {
        val (runClassName, input, directory, runexecExecutable, runexecVersion) = arguments
        repeatInThisProcess(runClassName, input, Path(directory), Runexec(runexecExecutable, runexecVersion))
        exitProcess(0)
    }

    internal fun repeatInThisProcess(
        runClassName: String,
        input: String,
        directory: Path,
        runexec: Runexec,
    ) {
        val runClass = Class.forName(runClassName)
        val run = try {
            val constructor = runClass.constructors.single()
            constructor.newInstance(benchmarkJson.decodeFromString(serializer(constructor.genericParameterTypes.single()), input)) as FreshJvmRun<*>
        } catch (throwable: Throwable) {
            writeFailure(directory, Failure(Stage.Setup, throwable.stackTraceToString()))
            return
        }
        val callsSerializer = ListSerializer(Call.serializer(resultSerializer(runClass)))
        val measurement = Measurement(runexec, null, directory)
        // a JVM cut at its time limit keeps the calls it finished
        measurement.writeFinishedCalls = {
            writeAtomically(directory.resolve(CALLS_FILE), benchmarkJson.encodeToString(callsSerializer, it))
        }
        try {
            val calls = measurement.calls(run.run(measurement))
            writeAtomically(directory.resolve(CALLS_FILE), benchmarkJson.encodeToString(callsSerializer, calls))
        } catch (throwable: Throwable) {
            writeFailure(directory, measurement.failure(throwable))
        }
    }

    private fun writeFailure(directory: Path, failure: Failure) {
        writeAtomically(directory.resolve(FAILURE_FILE), benchmarkJson.encodeToString(Failure.serializer(), failure))
    }

    @Suppress("UNCHECKED_CAST")
    private fun resultSerializer(runClass: Class<*>): KSerializer<Any?> {
        val runInterface = runClass.genericInterfaces.filterIsInstance<ParameterizedType>().single {
            it.rawType == FreshJvmRun::class.java
        }
        return serializer(runInterface.actualTypeArguments.single()) as KSerializer<Any?>
    }
}
