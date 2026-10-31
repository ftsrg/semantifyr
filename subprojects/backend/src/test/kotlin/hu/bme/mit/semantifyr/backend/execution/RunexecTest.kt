/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backend.execution

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class RunexecTest {

    @Test
    fun `a completed run gives the tool's return value and its measurements`() {
        val output = """
            2026-10-31 16:00:00 - INFO - Starting command theta-xsts-cli --model model.xsts
            starttime=2026-10-31T16:00:00.000000+01:00
            returnvalue=0
            walltime=1.250000000s
            cputime=2.500000000s
            cputime-cpu0=1.250000000s
            memory=123456789B
            blkio-read=0B
            pressure-cpu-some=0.000000s
        """.trimIndent()

        val run = RunexecRun.parse(output)

        assertThat(run).isEqualTo(
            RunexecRun(returnValue = 0, exitSignal = null, terminationReason = null, wallTime = 1250.milliseconds, cpuTime = 2.5.seconds, memory = 123456789),
        )
        assertThat(run.exitCode).isEqualTo(0)
        assertThat(run.isTimeout).isFalse()
    }

    @Test
    fun `a run killed on its time limit is a timeout with the signal as exit code`() {
        val run = RunexecRun.parse("terminationreason=cputime\nexitsignal=9\nwalltime=301s\ncputime=300.1s\nmemory=1000B\n")

        assertThat(run.isTimeout).isTrue()
        assertThat(run.exitCode).isEqualTo(137)
    }

    @Test
    fun `output without a result has no exit code`() {
        assertThat(RunexecRun.parse("2026-10-31 16:00:00 - CRITICAL - Cgroup subsystem cpuacct is not available").exitCode).isNull()
    }

    @Test
    fun `the tool runs through a shell that keeps its output files apart from runexec's`() {
        val runexec = Runexec(listOf("--no-container", "--memlimit", "8GB"))
        val workingDirectory = File("/work/case")
        val logFile = workingDirectory.resolve("spin.out")

        val command = runexec.wrap(listOf("spin", "-a", "model.pml"), workingDirectory, logFile, null)

        assertThat(command.take(9)).containsExactly(
            "runexec",
            "--no-container",
            "--memlimit",
            "8GB",
            "--dir",
            workingDirectory.absolutePath,
            "--output",
            workingDirectory.resolve("runexec.out").absolutePath,
            "--",
        )
        assertThat(command.takeLast(5)).containsExactly(logFile.absolutePath, "/dev/null", "spin", "-a", "model.pml")
    }
}
