/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.benchmark

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

class BenchmarkFileTest {

    private fun recordedRunexecOutput(name: String): String {
        return checkNotNull(javaClass.getResource("/runexec/$name.txt")).readText()
    }

    @Test
    fun `a finished process is read from runexec's output`() {
        val observation = ProcessObservation.parse(recordedRunexecOutput("finished"))

        assertThat(observation).isEqualTo(
            ProcessObservation(
                startTime = "2026-11-17T08:39:59.123918+01:00",
                wallTimeSeconds = 0.20866857992950827,
                cpuTimeSeconds = 0.003776,
                peakMemoryBytes = 520192,
                terminationReason = null,
                exitCode = 0,
                exitSignal = null,
                cpuPressureSeconds = 0.001636,
                ioPressureSeconds = 0.0,
                memoryPressureSeconds = 0.0,
            ),
        )
    }

    @Test
    fun `a process cut at its time limit is read with its signal and reason`() {
        val observation = ProcessObservation.parse(recordedRunexecOutput("cut-at-time-limit"))

        assertThat(observation.terminationReason).isEqualTo("walltime")
        assertThat(observation.exitSignal).isEqualTo(9)
        assertThat(observation.exitCode).isNull()
        assertThat(observation.wallTimeSeconds).isEqualTo(2.007904615951702)
    }

    @Test
    fun `a process runexec could not start is a failed measurement`() {
        assertThatThrownBy {
            ProcessObservation.parse(recordedRunexecOutput("failed-to-start"))
        }.isInstanceOf(MeasurementFailedException::class.java)
    }

    @Test
    fun `a written run reads back as it was written`() {
        val process = ProcessObservation.parse(recordedRunexecOutput("finished"))
        val revision = Revision("f3ecb96875af926bd3ad0d266e4ddca7cbb0442a", true)
        val run = BenchmarkRun(
            statement = Statement(
                benchmark = "CellBenchmark",
                configurations = mapOf(
                    "crossroads/all" to benchmarkJson.encodeToJsonElement(ModelCell.serializer(), ModelCell("crossroads", setOf("copy-propagation"))),
                ),
                repetitions = 5,
            ),
            startedAt = "2026-11-17T08:00:00Z",
            context = mapOf(
                CodeRevision.name to benchmarkJson.encodeToJsonElement(Revision.serializer(), revision),
            ),
            repetitions = listOf(
                Repetition(
                    configuration = "crossroads/all",
                    round = 0,
                    hostState = HostState(LoadAverage(1.5, 2.0, 2.5), LoadAverage(1.6, 2.0, 2.5)),
                    timeLimit = 60.seconds,
                    jvm = process,
                    calls = listOf(
                        Call(false, emptyList(), listOf(process), BackendAnswer("Passed", null)),
                    ),
                    failure = null,
                ),
                Repetition(
                    configuration = "crossroads/none",
                    round = 0,
                    hostState = HostState(LoadAverage(1.6, 2.0, 2.5), LoadAverage(1.7, 2.0, 2.5)),
                    timeLimit = null,
                    jvm = null,
                    calls = listOf(
                        Call(true, listOf(SpanObservation("flattening", 1200)), emptyList(), null),
                    ),
                    failure = Failure(Stage.Setup, "IllegalStateException: refused"),
                ),
            ),
        )
        val serializer = BenchmarkRun.serializer(BackendAnswer.serializer())

        val read = benchmarkJson.decodeFromString(serializer, benchmarkJson.encodeToString(serializer, run))

        assertThat(read).isEqualTo(run)
        assertThat(read.context(CodeRevision)).isEqualTo(revision)
    }
}
