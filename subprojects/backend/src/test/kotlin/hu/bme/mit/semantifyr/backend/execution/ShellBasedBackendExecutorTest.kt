/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backend.execution

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.condition.DisabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

class ShellBasedBackendExecutorTest {

    private val isWindows = System.getProperty("os.name").lowercase().contains("windows")

    @TempDir
    lateinit var temporaryDirectory: File

    private class StubExecutor(
        override val binaryName: String,
    ) : ShellBasedBackendExecutor() {

        suspend fun run(
            arguments: List<String>,
            workingDirectory: File,
            logFile: File? = null,
            errorFile: File? = null,
        ): Int {
            return runProcess(arguments, workingDirectory, logFile, errorFile)
        }

        fun probe(timeoutSeconds: Long = 2): Boolean {
            return probeAvailability(listOf("-V"), timeoutSeconds)
        }
    }

    private fun stub(): StubExecutor {
        val script = if (isWindows) {
            temporaryDirectory.resolve("stub.cmd").apply {
                writeText(
                    """
                        @echo off
                        if "%~1"=="-V" exit /b 0
                        if "%~1"=="--sleep" ping -n 30 127.0.0.1 > nul
                        echo cwd=%CD%
                        :next
                        if "%~1"=="" goto done
                        echo arg=%~1
                        shift
                        goto next
                        :done
                        echo stub error 1>&2
                        echo produced> produced.txt
                        exit /b 3
                    """.trimIndent().replace("\n", "\r\n"),
                )
            }
        } else {
            temporaryDirectory.resolve("stub.sh").apply {
                writeText(
                    """
                        #!/bin/sh
                        if [ "${'$'}1" = "-V" ]; then exit 0; fi
                        if [ "${'$'}1" = "--sleep" ]; then sleep 30; fi
                        echo "cwd=${'$'}(pwd -P)"
                        for argument in "${'$'}@"; do echo "arg=${'$'}argument"; done
                        echo "stub error" >&2
                        echo produced > produced.txt
                        exit 3
                    """.trimIndent() + "\n",
                )
                setExecutable(true)
            }
        }
        return StubExecutor(script.absolutePath)
    }

    private fun workingDirectory(vararg segments: String): File {
        return segments.fold(temporaryDirectory.resolve("work")) { directory, segment ->
            directory.resolve(segment)
        }.apply {
            mkdirs()
        }
    }

    private fun assertPrintedWorkingDirectory(line: String, workingDirectory: File) {
        assertThat(line).startsWith("cwd=")
        assertThat(Path.of(line.removePrefix("cwd=")).toRealPath()).isEqualTo(workingDirectory.toPath().toRealPath())
    }

    @Test
    suspend fun `the tool runs in the working directory with its arguments`() {
        val workingDirectory = workingDirectory()
        val logFile = workingDirectory.resolve("tool.out")

        val exitCode = stub().run(listOf("-search", "two words", "model.pml"), workingDirectory, logFile)

        assertThat(exitCode).isEqualTo(3)
        val lines = logFile.readLines().map { it.trim() }
        assertPrintedWorkingDirectory(lines.first(), workingDirectory)
        assertThat(lines.drop(1)).containsExactly(
            "arg=-search",
            "arg=two words",
            "arg=model.pml",
        )
        assertThat(workingDirectory.resolve("produced.txt")).exists()
    }

    @Test
    suspend fun `standard error goes to the error file`() {
        val workingDirectory = workingDirectory()
        val logFile = workingDirectory.resolve("tool.out")
        val errorFile = workingDirectory.resolve("tool.err")

        stub().run(emptyList(), workingDirectory, logFile, errorFile)

        assertThat(errorFile.readText().trim()).isEqualTo("stub error")
        assertThat(logFile.readText()).doesNotContain("stub error")
    }

    @Test
    suspend fun `runs append to existing output files`() {
        val workingDirectory = workingDirectory()
        val errorFile = workingDirectory.resolve("tool.err")

        stub().run(emptyList(), workingDirectory, errorFile = errorFile)
        stub().run(emptyList(), workingDirectory, errorFile = errorFile)

        assertThat(errorFile.readLines().map { it.trim() }).containsExactly("stub error", "stub error")
    }

    @Test
    suspend fun `output files are created in missing directories`() {
        val workingDirectory = workingDirectory()
        val logFile = temporaryDirectory.resolve("logs").resolve("nested").resolve("tool.out")

        stub().run(emptyList(), workingDirectory, logFile)

        assertThat(logFile).exists()
    }

    @Test
    @DisabledOnOs(OS.WINDOWS, disabledReason = "CreateProcess rejects a working directory over 260 characters, roadmap: Windows path length")
    suspend fun `the tool runs in a working directory as deep as a witness validation directory`() {
        val case = "semantifyr.sysml.examples.spacecraft.SpacecraftDataCanReachGroundStationWithinTwentyStepsViolated"
        val workingDirectory = workingDirectory(case, "validation", case, "theta-cegar-expl-pred-combined")
        assertThat(workingDirectory.absolutePath.length).isGreaterThan(260)
        val logFile = workingDirectory.resolve("tool.out")

        val exitCode = stub().run(listOf("model.xsts"), workingDirectory, logFile)

        assertThat(exitCode).isEqualTo(3)
        assertPrintedWorkingDirectory(logFile.readLines().first().trim(), workingDirectory)
    }

    @Test
    suspend fun `cancellation destroys the running tool`() {
        val workingDirectory = workingDirectory()
        val mark = TimeSource.Monotonic.markNow()

        assertThrows<TimeoutCancellationException> {
            withTimeout(500.milliseconds) {
                stub().run(listOf("--sleep"), workingDirectory)
            }
        }

        assertThat(mark.elapsedNow()).isLessThan(10.seconds)
        assertThat(workingDirectory.resolve("produced.txt")).doesNotExist()
    }

    @Test
    fun `the probe succeeds when the tool answers with exit code zero`() {
        assertThat(stub().probe()).isTrue()
    }

    @Test
    fun `the probe fails when the tool is missing`() {
        assertThat(StubExecutor(temporaryDirectory.resolve("missing-tool").absolutePath).probe()).isFalse()
    }
}
