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
import kotlin.io.path.readText
import kotlin.io.path.setPosixFilePermissions
import kotlin.io.path.writeText

class SwallowingBenchmark : BenchmarkSource {
    override val benchmark = benchmark(
        configurations = mapOf(
            "crossroads/all" to ModelCell("crossroads", emptySet()),
        ),
        isolation = RunnerJvm,
    ) {
        Trial { measurement ->
            val directory = measurement.processes.directory
            try {
                measurement.processes.run(listOf("echo"), directory, directory.resolve("out"), directory.resolve("err"))
                BackendAnswer("Passed", null)
            } catch (exception: Exception) {
                BackendAnswer("Errored", exception.message)
            }
        }
    }
}

class OverflowingSetupBenchmark : BenchmarkSource {
    override val benchmark = benchmark<ModelCell, BackendAnswer>(
        configurations = mapOf(
            "crossroads/all" to ModelCell("crossroads", emptySet()),
            "crossroads/none" to ModelCell("crossroads", emptySet()),
        ),
        isolation = RunnerJvm,
    ) {
        throw StackOverflowError("setup overflowed")
    }
}

class OutOfMemorySetupBenchmark : BenchmarkSource {
    override val benchmark = benchmark<ModelCell, BackendAnswer>(
        configurations = mapOf(
            "crossroads/all" to ModelCell("crossroads", emptySet()),
        ),
        isolation = FreshJvm(),
    ) {
        throw OutOfMemoryError("setup ran out of memory")
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

    private fun run(source: BenchmarkSource, runexec: String): BenchmarkRun<ModelCell, BackendAnswer> {
        val file = runBenchmark(source, runexec, directory.resolve("benchmark"))
        return benchmarkJson.decodeFromString(BenchmarkRun.serializer(ModelCell.serializer(), BackendAnswer.serializer()), file.readText())
    }

    @Test
    fun `a measurement runexec fails is the repetition's failure and not the result the benchmark made of it`() {
        val run = run(SwallowingBenchmark(), standIn("measuring-nothing.sh"))

        assertThat(run.repetitions).hasSize(5).allSatisfy {
            assertThat(it.failure?.stage).isEqualTo(Stage.Measurement)
            assertThat(it.calls).isEmpty()
            assertThat(it.jvm).isNull()
        }
    }

    @Test
    fun `an error thrown in a process benchmark's setup is that repetition's failed setup and the run goes on`() {
        val run = run(OverflowingSetupBenchmark(), standIn("stand-in.sh"))

        assertThat(run.repetitions).hasSize(10).allSatisfy {
            assertThat(it.failure?.stage).isEqualTo(Stage.Setup)
            assertThat(it.failure?.stackTrace).contains("StackOverflowError")
        }
    }

    @Test
    fun `an error thrown in a fresh JVM's setup reaches its calls file`() {
        val run = run(OutOfMemorySetupBenchmark(), standIn("stand-in.sh"))

        assertThat(run.repetitions).hasSize(5).allSatisfy {
            assertThat(it.failure?.stage).isEqualTo(Stage.Setup)
            assertThat(it.failure?.stackTrace).contains("OutOfMemoryError")
            assertThat(it.jvm).isNotNull()
        }
    }
}
