/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.benchmark

import kotlinx.serialization.builtins.ListSerializer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readText
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

class CutRun(cell: ModelCell) : FreshJvmRun<BackendAnswer> {
    private val model = cell.model

    override fun run(measurement: Measurement): BackendAnswer {
        repeat(2) {
            measurement.warmUp().spans.measure(model) {
                model.length
            }
        }
        measurement.measured()
        throw Error("cut at the time limit")
    }
}

class StoppingBenchmark : Benchmark<BackendAnswer>() {
    override fun measure(): BenchmarkRun<BackendAnswer> {
        val run = BenchmarkRun<BackendAnswer>()
        val measured = inThisJvm {
            BackendAnswer("Passed", null)
        }
        run.add("crossroads/all", ModelCell("crossroads", emptySet()), measured)
        error("the prework of the next repetition failed")
    }
}

class BenchmarkRunnerTest {

    @TempDir
    lateinit var directory: Path

    private fun standIn(name: String): String {
        val script = directory.resolve(name)
        script.writeText(checkNotNull(javaClass.getResource("/runexec/$name")).readText())
        script.setPosixFilePermissions(PosixFilePermissions.fromString("rwx------"))
        return script.toString()
    }

    private fun start(runexec: String) {
        BenchmarkEnvironment.start(Runexec.probe(runexec), directory.resolve("benchmark"), null)
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    fun `a measurement runexec fails is the repetition's failure and not the result the benchmark made of it`() {
        start(standIn("measuring-nothing.sh"))

        val measured = inThisJvm { measurement ->
            val processes = measurement.measured().processes
            try {
                processes.run(listOf("echo"), processes.directory, processes.directory.resolve("out"), processes.directory.resolve("err"))
                BackendAnswer("Passed", null)
            } catch (exception: Exception) {
                BackendAnswer("Errored", exception.message)
            }
        }

        assertThat(measured.failure?.stage).isEqualTo(Stage.Measurement)
        assertThat(measured.calls).isEmpty()
        assertThat(measured.jvm).isNull()
    }

    @Test
    fun `an error thrown while a fresh JVM creates its run is its failed setup`() {
        val input = benchmarkJson.encodeToString(ModelCell.serializer(), ModelCell("crossroads", emptySet()))

        FreshJvm.repeatInThisProcess(OutOfMemoryRun::class.java.name, input, directory, Runexec("runexec", "runexec 3.35"))

        val failure = benchmarkJson.decodeFromString(Failure.serializer(), directory.resolve("failure.json").readText())
        assertThat(failure.stage).isEqualTo(Stage.Setup)
        assertThat(failure.stackTrace).contains("OutOfMemoryError")
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    fun `a measure that stops leaves the repetitions added before it in the run's file`() {
        val outputDirectory = directory.resolve("benchmark")

        assertThatThrownBy {
            measureAndWrite(StoppingBenchmark(), standIn("stand-in.sh"), outputDirectory)
        }.hasMessageContaining("prework")

        val file = outputDirectory.resolve("StoppingBenchmark").listDirectoryEntries("*.json").single()
        val run = benchmarkJson.decodeFromString(BenchmarkRun.serializer(BackendAnswer.serializer()), file.readText())
        assertThat(run.repetitions).hasSize(1)
        assertThat(run.context(CodeRevision)).isNotNull()
    }

    @Test
    fun `a fresh JVM keeps the calls it finished before it stops`() {
        val input = benchmarkJson.encodeToString(ModelCell.serializer(), ModelCell("crossroads", emptySet()))

        FreshJvm.repeatInThisProcess(CutRun::class.java.name, input, directory, Runexec("runexec", "runexec 3.35"))

        val calls = benchmarkJson.decodeFromString(ListSerializer(Call.serializer(BackendAnswer.serializer())), directory.resolve("calls.json").readText())
        assertThat(calls).hasSize(2).allSatisfy {
            assertThat(it.warmUp).isTrue()
            assertThat(it.spans).hasSize(1)
        }
        assertThat(directory.resolve("failure.json")).exists()
    }
}
