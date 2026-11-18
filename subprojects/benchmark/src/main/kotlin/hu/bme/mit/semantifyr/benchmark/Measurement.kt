/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.benchmark

import java.nio.file.Path
import kotlin.time.Duration
import kotlin.time.TimeSource

class Measurement internal constructor(
    runexec: Runexec,
    timeLimit: Duration?,
    directory: Path,
) {
    val spans = Spans()

    val processes = MeasuredProcesses(runexec, timeLimit, directory)

    internal fun <R> call(trial: Trial<R>, warmUp: Boolean): Call<R> {
        val result = trial.call(this)
        val measurementFailure = processes.measurementFailure
        if (measurementFailure != null) {
            throw measurementFailure
        }
        return Call(warmUp, spans.observations.toList(), processes.observations.toList(), result)
    }

    internal fun failure(throwable: Throwable): Failure {
        val measurementFailure = processes.measurementFailure
        if (measurementFailure != null || throwable is MeasurementFailedException) {
            return Failure(Stage.Measurement, (measurementFailure ?: throwable).stackTraceToString())
        }
        return Failure(Stage.Call, throwable.stackTraceToString())
    }
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
    timeLimit: Duration?,
    val directory: Path,
) {
    internal val observations = mutableListOf<ProcessObservation>()

    internal var measurementFailure: MeasurementFailedException? = null

    private val deadline = timeLimit?.let {
        TimeSource.Monotonic.markNow() + it
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
        try {
            val observation = runexec.run(command, workingDirectory, outputFile, errorFile, timeLeft)
            observations += observation
            return observation
        } catch (exception: MeasurementFailedException) {
            measurementFailure = exception
            throw exception
        }
    }
}
