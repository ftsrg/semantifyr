/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.benchmark

import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.time.Duration
import kotlin.time.TimeMark
import kotlin.time.TimeSource

class Measurement internal constructor(
    private val runexec: Runexec,
    timeLimit: Duration?,
    private val directory: Path,
) {
    private val deadline = timeLimit?.let {
        TimeSource.Monotonic.markNow() + it
    }

    private val opened = mutableListOf<Instruments>()

    internal var writeFinishedCalls: ((List<Call<Any?>>) -> Unit)? = null

    fun warmUp(): Instruments {
        return open(true)
    }

    fun measured(): Instruments {
        return open(false)
    }

    private fun open(warmUp: Boolean): Instruments {
        writeFinishedCalls?.invoke(
            opened.map {
                Call(it.warmUp, it.spans.observations.toList(), it.processes.observations.toList(), null)
            },
        )
        val instruments = Instruments(warmUp, runexec, deadline, directory.resolve("call-${opened.size + 1}").createDirectories())
        opened += instruments
        return instruments
    }

    internal fun <R> calls(result: R): List<Call<R>> {
        val measurementFailure = opened.firstNotNullOfOrNull {
            it.processes.measurementFailure
        }
        if (measurementFailure != null) {
            throw measurementFailure
        }
        val measuredOpened = opened.any {
            !it.warmUp
        }
        if (!measuredOpened) {
            open(false)
        }
        val resultIndex = opened.indexOfLast {
            !it.warmUp
        }
        return opened.mapIndexed { index, instruments ->
            Call(
                warmUp = instruments.warmUp,
                spans = instruments.spans.observations.toList(),
                processes = instruments.processes.observations.toList(),
                result = result.takeIf {
                    index == resultIndex
                },
            )
        }
    }

    internal fun failure(throwable: Throwable): Failure {
        val measurementFailure = opened.firstNotNullOfOrNull {
            it.processes.measurementFailure
        }
        if (measurementFailure != null || throwable is MeasurementFailedException) {
            return Failure(Stage.Measurement, (measurementFailure ?: throwable).stackTraceToString())
        }
        return Failure(Stage.Call, throwable.stackTraceToString())
    }
}

class Instruments internal constructor(
    internal val warmUp: Boolean,
    runexec: Runexec,
    deadline: TimeMark?,
    directory: Path,
) {
    val spans = Spans()

    val processes = MeasuredProcesses(runexec, deadline, directory)
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
    private val deadline: TimeMark?,
    val directory: Path,
) {
    internal val observations = mutableListOf<ProcessObservation>()

    internal var measurementFailure: MeasurementFailedException? = null

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

class Measured<R> internal constructor(
    internal val hostState: HostState,
    internal val timeLimit: Duration?,
    internal val jvm: ProcessObservation?,
    internal val calls: List<Call<R>>,
    internal val failure: Failure?,
)

fun <R> inThisJvm(timeLimit: Duration? = null, call: (Measurement) -> R): Measured<R> {
    val loadBefore = LoadAverage.now()
    val measurement = Measurement(BenchmarkEnvironment.runexec, timeLimit, BenchmarkEnvironment.nextDirectory())
    try {
        val calls = measurement.calls(call(measurement))
        return Measured(HostState(loadBefore, LoadAverage.now()), timeLimit, null, calls, null)
    } catch (throwable: Throwable) {
        return Measured(HostState(loadBefore, LoadAverage.now()), timeLimit, null, emptyList(), measurement.failure(throwable))
    }
}
