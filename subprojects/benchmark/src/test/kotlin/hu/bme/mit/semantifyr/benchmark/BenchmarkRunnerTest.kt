/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.benchmark

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import kotlin.io.path.setPosixFilePermissions
import kotlin.io.path.writeText

class OutOfMemoryRun(cell: ModelCell) : FreshJvmRun<BackendAnswer> {
    init {
        throw OutOfMemoryError("setup of ${cell.model} ran out of memory")
    }

    override fun run(measurement: Measurement): BackendAnswer {
        return BackendAnswer("Passed", null)
    }
}

class BenchmarkRunnerTest {

    @TempDir
    lateinit var directory: Path

    private val cells = mapOf(
        "crossroads/all" to ModelCell("crossroads", emptySet()),
        "crossroads/none" to ModelCell("crossroads", emptySet()),
    )

    private fun standIn(name: String): String {
        val script = directory.resolve(name)
        script.writeText(checkNotNull(javaClass.getResource("/runexec/$name")).readText())
        script.setPosixFilePermissions(PosixFilePermissions.fromString("rwx------"))
        return script.toString()
    }

    private fun run(runexec: String, repetition: RepetitionScope.(ModelCell) -> Measured<BackendAnswer>): BenchmarkRun<BackendAnswer> {
        return runInterleavedRounds(
            name = "TestBenchmark",
            cases = cells,
            repetitions = 5,
            context = emptyList(),
            configurationSerializer = ModelCell.serializer(),
            resultSerializer = BackendAnswer.serializer(),
            runexecExecutable = runexec,
            outputDirectory = directory.resolve("benchmark"),
            repetition = repetition,
        )
    }

    @Test
    fun `a measurement runexec fails is the repetition's failure and not the result the benchmark made of it`() {
        val run = run(standIn("measuring-nothing.sh")) {
            inThisJvm { measurement ->
                val processes = measurement.measured().processes
                try {
                    processes.run(listOf("echo"), processes.directory, processes.directory.resolve("out"), processes.directory.resolve("err"))
                    BackendAnswer("Passed", null)
                } catch (exception: Exception) {
                    BackendAnswer("Errored", exception.message)
                }
            }
        }

        assertThat(run.repetitions).hasSize(10).allSatisfy {
            assertThat(it.failure?.stage).isEqualTo(Stage.Measurement)
            assertThat(it.calls).isEmpty()
            assertThat(it.jvm).isNull()
        }
    }

    @Test
    fun `an error thrown in a repetition's prework is that repetition's failed setup and the run goes on`() {
        val run = run(standIn("stand-in.sh")) {
            throw StackOverflowError("setup overflowed")
        }

        assertThat(run.repetitions).hasSize(10).allSatisfy {
            assertThat(it.failure?.stage).isEqualTo(Stage.Setup)
            assertThat(it.failure?.stackTrace).contains("StackOverflowError")
        }
    }

    @Test
    fun `an error thrown while a fresh JVM creates its run reaches the repetition`() {
        val run = run(standIn("stand-in.sh")) { cell ->
            freshJvm(OutOfMemoryRun::class, cell)
        }

        assertThat(run.repetitions).hasSize(10).allSatisfy {
            assertThat(it.failure?.stage).isEqualTo(Stage.Setup)
            assertThat(it.failure?.stackTrace).contains("OutOfMemoryError")
            assertThat(it.jvm).isNotNull()
        }
    }
}
