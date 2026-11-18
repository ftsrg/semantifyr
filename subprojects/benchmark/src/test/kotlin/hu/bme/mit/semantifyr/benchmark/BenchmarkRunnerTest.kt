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

class SwallowingBenchmark : ProcessBenchmark<ModelCell, BackendAnswer>() {
    override val configurations = mapOf(
        "crossroads/all" to ModelCell("crossroads", emptySet()),
    )

    override fun repetition(processes: MeasuredProcesses): BackendAnswer {
        try {
            processes.run(listOf("echo"), processes.directory, processes.directory.resolve("out"), processes.directory.resolve("err"))
        } catch (exception: Exception) {
            return BackendAnswer("Errored", exception.message)
        }
        return BackendAnswer("Passed", null)
    }
}

class OverflowingSetupBenchmark : ProcessBenchmark<ModelCell, BackendAnswer>() {
    override val configurations = mapOf(
        "crossroads/all" to ModelCell("crossroads", emptySet()),
        "crossroads/none" to ModelCell("crossroads", emptySet()),
    )

    override fun setUp(configuration: ModelCell) {
        super.setUp(configuration)
        throw StackOverflowError("setup overflowed")
    }

    override fun repetition(processes: MeasuredProcesses): BackendAnswer {
        return BackendAnswer("Passed", null)
    }
}

class OutOfMemorySetupBenchmark : InJvmBenchmark<ModelCell, BackendAnswer>() {
    override val configurations = mapOf(
        "crossroads/all" to ModelCell("crossroads", emptySet()),
    )

    override fun setUp(configuration: ModelCell) {
        super.setUp(configuration)
        throw OutOfMemoryError("setup ran out of memory")
    }

    override fun call(spans: Spans): BackendAnswer {
        return BackendAnswer("Passed", null)
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

    private fun run(benchmark: Benchmark<*, *>, runexec: String): BenchmarkRun<ModelCell, BackendAnswer> {
        return readBenchmarkRun(runBenchmark(benchmark, runexec, directory.resolve("benchmark")))
    }

    @Test
    fun `a measurement runexec fails is the repetition's failure and not the result the benchmark made of it`() {
        val run = run(SwallowingBenchmark(), standIn("measuring-nothing.sh"))

        assertThat(run.repetitions).hasSize(5).allSatisfy {
            assertThat(it.failure?.stage).isEqualTo(Stage.Measurement)
            assertThat(it.calls).isEmpty()
            assertThat(it.processes).isEmpty()
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
            assertThat(it.processes).hasSize(1)
        }
    }
}
