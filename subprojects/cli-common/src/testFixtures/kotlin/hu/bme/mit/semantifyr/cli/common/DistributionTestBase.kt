/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.cli.common

import hu.bme.mit.semantifyr.backends.theta.ThetaExecutorKey
import hu.bme.mit.semantifyr.verifier.assumeBackendAvailable
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readText

class LauncherRun(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
)

abstract class DistributionTestBase(
    private val launcher: String,
) {
    protected val home: Path = Path("build/install").listDirectoryEntries().single().toAbsolutePath()

    private val windows = System.getProperty("os.name").startsWith("Windows")

    @TempDir
    lateinit var directory: Path

    @BeforeEach
    fun assumeThetaAvailable() {
        assumeBackendAvailable(ThetaExecutorKey)
    }

    protected fun run(vararg arguments: String): LauncherRun {
        val command = if (windows) {
            listOf("cmd.exe", "/c", home.resolve("bin/$launcher.bat").toString())
        } else {
            listOf(home.resolve("bin/$launcher").toString())
        }
        val javaHome = System.getProperty("java.home")
        val stdout = directory.resolve("stdout.txt")
        val stderr = directory.resolve("stderr.txt")
        val processBuilder = ProcessBuilder(command + arguments)
            .directory(directory.toFile())
            .redirectOutput(stdout.toFile())
            .redirectError(stderr.toFile())
        val environment = processBuilder.environment()
        environment["JAVA_HOME"] = javaHome
        environment["PATH"] = "${Path(javaHome, "bin")}${File.pathSeparator}${System.getenv("PATH")}"
        val process = processBuilder.start()
        check(process.waitFor(10, TimeUnit.MINUTES)) {
            "$launcher ${arguments.joinToString(" ")} did not exit"
        }
        return LauncherRun(process.exitValue(), stdout.readText(), stderr.readText())
    }

    protected fun reportOf(run: LauncherRun): Path {
        return Path(jsonLineFormat.decodeFromString<VerifyOutcomeLine>(run.stdout.trim()).report)
    }

    @Test
    fun `the launcher prints its version without JVM warnings`() {
        val run = run("--version")

        assertThat(run.exitCode).isZero()
        assertThat(run.stdout.trim()).matches("$launcher version \\d+\\.\\d+\\.\\d+.*")
        assertThat(run.stderr).isEmpty()
    }

    @Test
    fun `a missing model is an input error`() {
        assertThat(run("verify", directory.resolve("absent").toString()).exitCode).isEqualTo(ExitCode.Input.code)
    }
}
