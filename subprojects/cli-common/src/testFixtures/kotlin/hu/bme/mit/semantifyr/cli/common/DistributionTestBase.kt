/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.cli.common

import hu.bme.mit.semantifyr.backends.theta.ThetaExecutorKey
import hu.bme.mit.semantifyr.verifier.assumeBackendAvailable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.isExecutable
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText

class LauncherRun(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
)

abstract class DistributionTestBase(
    private val launcher: String,
) {
    protected val home: Path = Path("build/unzipped").listDirectoryEntries().single().toAbsolutePath()

    private val osName = System.getProperty("os.name")
    private val windows = osName.startsWith("Windows")

    @TempDir
    lateinit var directory: Path

    @BeforeEach
    fun assumeThetaAvailable() {
        assumeBackendAvailable(ThetaExecutorKey)
    }

    // without Theta on PATH, a verdict proves that the launcher puts the bundled Theta there
    protected fun run(vararg arguments: String): LauncherRun {
        val command = if (windows) {
            listOf("cmd.exe", "/c", home.resolve("bin/$launcher.bat").toString())
        } else {
            listOf(home.resolve("bin/$launcher").toString())
        }
        val javaHome = System.getProperty("java.home")
        val path = System.getenv("PATH").split(File.pathSeparator).filterNot {
            runCatching { Path(it).resolve("theta-xsts-cli").exists() }.getOrDefault(false)
        }
        val stdout = directory.resolve("stdout.txt")
        val stderr = directory.resolve("stderr.txt")
        val process = ProcessBuilder(command + arguments)
            .directory(directory.toFile())
            .redirectOutput(stdout.toFile())
            .redirectError(stderr.toFile())
            .apply {
                environment()["JAVA_HOME"] = javaHome
                environment()["PATH"] = (listOf(Path(javaHome, "bin").toString()) + path).joinToString(File.pathSeparator)
            }
            .start()
        check(process.waitFor(10, TimeUnit.MINUTES)) { "$launcher ${arguments.joinToString(" ")} did not exit" }
        return LauncherRun(process.exitValue(), stdout.readText(), stderr.readText())
    }

    protected fun reportOf(run: LauncherRun): Path {
        val line = Json.parseToJsonElement(run.stdout.trim()).jsonObject
        return Path(line.getValue("report").jsonPrimitive.content)
    }

    @Test
    fun `the launcher prints its version without JVM warnings`() {
        val run = run("--version")

        assertThat(run.exitCode).isZero()
        assertThat(run.stdout.trim()).matches("$launcher version \\d+\\.\\d+\\.\\d+.*")
        assertThat(run.stderr).isEmpty()
    }

    @Test
    fun `the bundled Theta carries the native libraries of this platform`() {
        val extension = when {
            windows -> "dll"
            osName.startsWith("Mac") -> "dylib"
            else -> "so"
        }
        val libraries = home.resolve("theta-xsts-cli/lib").listDirectoryEntries().map { it.name }

        assertThat(libraries)
            .contains("libz3.$extension", "libz3java.$extension", "libz3legacy.$extension", "libz3javalegacy.$extension")
            .allMatch { it.endsWith(".$extension") }
        if (!windows) {
            assertThat(home.resolve("theta-xsts-cli/theta-xsts-cli")).isExecutable()
        }
    }

    @Test
    fun `a missing model is an input error`() {
        assertThat(run("verify", directory.resolve("absent").toString()).exitCode).isEqualTo(ExitCode.Input.code)
    }
}
