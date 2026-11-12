/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backend.execution

import kotlin.random.Random
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
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
                        if "%~1"=="--sleep" (
                            echo sleeping> sleeping.txt
                            ping -n 30 127.0.0.1 > nul
                        )
                        echo workingDirectory=%CD%
                        :next
                        if "%~1"=="" goto done
                        echo argument=%~1
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
                        if [ "${'$'}1" = "--sleep" ]; then echo sleeping > sleeping.txt; sleep 30; fi
                        echo "workingDirectory=${'$'}(pwd -P)"
                        for argument in "${'$'}@"; do echo "argument=${'$'}argument"; done
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
        assertThat(line).startsWith("workingDirectory=")
        assertThat(Path.of(line.removePrefix("workingDirectory=")).toRealPath()).isEqualTo(workingDirectory.toPath().toRealPath())
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
            "argument=-search",
            "argument=two words",
            "argument=model.pml",
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
    @DisabledOnOs(OS.WINDOWS, disabledReason = "CreateProcess rejects a working directory over 260 characters, which the verifier's short case directory names avoid")
    suspend fun `the tool runs in a working directory over 260 characters`() {
        val workingDirectory = workingDirectory("a".repeat(100), "b".repeat(100), "c".repeat(100))
        assertThat(workingDirectory.absolutePath.length).isGreaterThan(260)
        val logFile = workingDirectory.resolve("tool.out")

        val exitCode = stub().run(listOf("model.xsts"), workingDirectory, logFile)

        assertThat(exitCode).isEqualTo(3)
        assertPrintedWorkingDirectory(logFile.readLines().first().trim(), workingDirectory)
    }

    @Test
    suspend fun `cancellation destroys the running tool`() {
        val workingDirectory = workingDirectory()

        coroutineScope {
            val run = async {
                stub().run(listOf("--sleep"), workingDirectory)
            }
            withTimeout(10.seconds) {
                while (!workingDirectory.resolve("sleeping.txt").exists()) {
                    delay(10.milliseconds)
                }
            }
            val mark = TimeSource.Monotonic.markNow()

            run.cancelAndJoin()

            assertThat(mark.elapsedNow()).isLessThan(10.seconds)
            assertThat(run.isCancelled).isTrue()
        }

        assertThat(workingDirectory.resolve("produced.txt")).doesNotExist()
    }

    private fun launcher(name: String, body: String): StubExecutor {
        val script = temporaryDirectory.resolve(name).apply {
            writeText("#!/bin/bash\n$body\n")
            setExecutable(true)
        }
        return StubExecutor(script.absolutePath)
    }

    private fun liveProcessesWithArgument(argument: String): List<ProcessHandle> {
        return ProcessHandle.allProcesses().filter {
            it.isAlive && it.info().arguments().orElse(emptyArray()).contains(argument)
        }.toList()
    }

    @Test
    @DisabledOnOs(OS.WINDOWS, disabledReason = "the launchers are shell scripts")
    suspend fun `a run cancelled before it starts spawns no process`() {
        val workingDirectory = workingDirectory()

        coroutineScope {
            val run = async(start = CoroutineStart.UNDISPATCHED) {
                currentCoroutineContext().job.cancel()
                launcher("started.sh", "touch started.txt").run(emptyList(), workingDirectory)
            }
            run.join()
        }

        assertThat(workingDirectory.resolve("started.txt")).doesNotExist()
    }

    @Test
    @DisabledOnOs(OS.WINDOWS, disabledReason = "the launchers are shell scripts")
    suspend fun `a run cancelled at once leaves no live descendant`() {
        val executor = launcher("launcher.sh", "exec sleep \"\$1\"")
        val workingDirectory = workingDirectory()
        val base = 3600 + Random.nextInt(1000)

        fun marker(iteration: Int): String {
            return "$base.$iteration"
        }

        try {
            repeat(300) {
                coroutineScope {
                    val run = async(start = CoroutineStart.UNDISPATCHED) {
                        executor.run(listOf(marker(it)), workingDirectory)
                    }
                    run.cancelAndJoin()
                }
            }

            val survivors = (0..<300).flatMap {
                liveProcessesWithArgument(marker(it))
            }
            assertThat(survivors).isEmpty()
        } finally {
            (0..<300).flatMap {
                liveProcessesWithArgument(marker(it))
            }.forEach {
                it.destroyForcibly()
            }
        }
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
