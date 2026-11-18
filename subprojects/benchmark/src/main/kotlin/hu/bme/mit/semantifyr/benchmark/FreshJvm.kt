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

inline fun <reified I, reified R> RepetitionScope.freshJvm(
    run: KClass<out FreshJvmRun<R>>,
    input: I,
    timeLimit: Duration? = null,
): Measured<R> {
    return startFreshJvm(this, run.java.name, benchmarkJson.encodeToString(serializer<I>(), input), serializer<R>(), timeLimit)
}

@PublishedApi
internal fun <R> startFreshJvm(
    scope: RepetitionScope,
    runClass: String,
    input: String,
    resultSerializer: KSerializer<R>,
    timeLimit: Duration?,
): Measured<R> {
    val java = Path(System.getProperty("java.home"), "bin", "java").toString()
    val arguments = listOf(
        "-cp",
        System.getProperty("java.class.path"),
        FreshJvm::class.java.name,
        runClass,
        input,
        scope.directory.toString(),
        scope.runexec.executable,
        scope.runexec.version,
    )
    val command = listOf(java) + ManagementFactory.getRuntimeMXBean().inputArguments + arguments
    val directory = scope.directory
    // in the task's working directory, where a benchmark's relative paths point
    val jvm = try {
        scope.runexec.run(command, Path("").toAbsolutePath(), directory.resolve("jvm.out"), directory.resolve("jvm.err"), timeLimit)
    } catch (exception: MeasurementFailedException) {
        return Measured(null, emptyList(), Failure(Stage.Measurement, exception.stackTraceToString()), timeLimit)
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
    return Measured(jvm, calls, failure, timeLimit)
}

object FreshJvm {
    @JvmStatic
    fun main(arguments: Array<String>) {
        val (runClassName, input, directoryName, runexecExecutable, runexecVersion) = arguments
        val directory = Path(directoryName)
        val runClass = Class.forName(runClassName)
        val run = try {
            val constructor = runClass.constructors.single()
            constructor.newInstance(benchmarkJson.decodeFromString(serializer(constructor.genericParameterTypes.single()), input)) as FreshJvmRun<*>
        } catch (throwable: Throwable) {
            writeFailure(directory, Failure(Stage.Setup, throwable.stackTraceToString()))
            exitProcess(0)
        }
        val measurement = Measurement(Runexec(runexecExecutable, runexecVersion), null, directory)
        try {
            val calls = measurement.calls(run.run(measurement))
            writeAtomically(directory.resolve(CALLS_FILE), benchmarkJson.encodeToString(ListSerializer(Call.serializer(resultSerializer(runClass))), calls))
        } catch (throwable: Throwable) {
            writeFailure(directory, measurement.failure(throwable))
        }
        exitProcess(0)
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
