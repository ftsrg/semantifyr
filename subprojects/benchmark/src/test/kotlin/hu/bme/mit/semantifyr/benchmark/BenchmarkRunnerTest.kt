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

    private fun standIn(name: String): String {
        val script = directory.resolve(name)
        script.writeText(checkNotNull(javaClass.getResource("/runexec/$name")).readText())
        script.setPosixFilePermissions(PosixFilePermissions.fromString("rwx------"))
        return script.toString()
    }

    private fun start(runexec: String) {
        BenchmarkEnvironment.start(Runexec.probe(runexec), directory.resolve("benchmark"))
    }

    @Test
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
    fun `an error thrown while a fresh JVM creates its run reaches the repetition`() {
        start(standIn("stand-in.sh"))

        val measured = freshJvm(OutOfMemoryRun::class, ModelCell("crossroads", emptySet()))

        assertThat(measured.failure?.stage).isEqualTo(Stage.Setup)
        assertThat(measured.failure?.stackTrace).contains("OutOfMemoryError")
        assertThat(measured.jvm).isNotNull()
    }
}
