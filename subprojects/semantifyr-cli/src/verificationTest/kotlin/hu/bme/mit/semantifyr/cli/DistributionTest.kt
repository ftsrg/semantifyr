/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.cli

import hu.bme.mit.semantifyr.backends.theta.ThetaExecutorKey
import hu.bme.mit.semantifyr.verifier.assumeBackendAvailable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.readText

class DistributionTest {

    companion object {
        private val home = Path("build/install/semantifyr").toAbsolutePath()
        private val testModels = Path("../../oxsts-test-models").toAbsolutePath().normalize()
        private val windows = System.getProperty("os.name").startsWith("Windows")

        @JvmStatic
        @BeforeAll
        fun assumeThetaAvailable() {
            assumeBackendAvailable(ThetaExecutorKey)
        }
    }

    @TempDir
    lateinit var directory: Path

    private class Run(val exitCode: Int, val stdout: String, val stderr: String)

    // without Theta on PATH, a verdict proves that the launcher puts the bundled Theta there
    private fun semantifyr(vararg arguments: String): Run {
        val launcher = if (windows) {
            listOf("cmd.exe", "/c", home.resolve("bin/semantifyr.bat").toString())
        } else {
            listOf(home.resolve("bin/semantifyr").toString())
        }
        val javaHome = System.getProperty("java.home")
        val path = System.getenv("PATH").split(File.pathSeparator).filterNot {
            runCatching { Path(it).resolve("theta-xsts-cli").exists() }.getOrDefault(false)
        }
        val stdout = directory.resolve("stdout.txt")
        val stderr = directory.resolve("stderr.txt")
        val process = ProcessBuilder(launcher + arguments)
            .directory(directory.toFile())
            .redirectOutput(stdout.toFile())
            .redirectError(stderr.toFile())
            .apply {
                environment()["JAVA_HOME"] = javaHome
                environment()["PATH"] = (listOf(Path(javaHome, "bin").toString()) + path).joinToString(File.pathSeparator)
            }
            .start()
        check(process.waitFor(10, TimeUnit.MINUTES)) { "semantifyr ${arguments.joinToString(" ")} did not exit" }
        return Run(process.exitValue(), stdout.readText(), stderr.readText())
    }

    @Test
    fun `the launcher prints the version without JVM warnings`() {
        val run = semantifyr("--version")

        assertThat(run.exitCode).isZero()
        assertThat(run.stdout.trim()).matches("semantifyr version \\d+\\.\\d+\\.\\d+.*")
        assertThat(run.stderr).isEmpty()
    }

    @Test
    fun `the bundled smoke model passes with the bundled Theta`() {
        val run = semantifyr("verify", "-q", "--theta-executor", "shell", "--portfolio", "theta-cegar-expl-pred-combined", "-d", "artifacts", home.resolve("models/smoke").toString())

        assertThat(run.exitCode).describedAs(run.stderr).isZero()
        assertThat(run.stdout.trim()).isEqualTo("PASSED")
        assertThat(run.stderr).doesNotContain("WARNING")
    }

    @Test
    fun `a violated property exits with the failed code and names its report`() {
        val run = semantifyr(
            "verify", "--json", "--theta-executor", "shell", "--portfolio", "theta-cegar-expl-pred-combined", "-d", "artifacts",
            "--case-name", "semantifyr::verification::lang::local_vars::LocalVarUninitializedBound",
            testModels.resolve("semantic/local_vars.oxsts").toString(),
        )

        assertThat(run.exitCode).describedAs(run.stderr).isEqualTo(5)
        val line = Json.parseToJsonElement(run.stdout.trim()).jsonObject
        assertThat(line.getValue("exitCode").jsonPrimitive.int).isEqualTo(5)
        val report = Path(line.getValue("report").jsonPrimitive.content)
        val result = Json.parseToJsonElement(report.readText()).jsonObject.getValue("result").jsonObject
        assertThat(result.getValue("verdict").jsonPrimitive.content).isEqualTo("Failed")
        assertThat(report.resolveSibling("witness.json")).exists()
    }

    @Test
    fun `the process exits with the usage and input codes`() {
        assertThat(semantifyr("verify", "--portfolio", "no-such-portfolio", "models").exitCode).isEqualTo(2)
        assertThat(semantifyr("list", directory.resolve("absent.oxsts").toString()).exitCode).isEqualTo(3)
    }
}
