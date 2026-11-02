/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.distribution

import hu.bme.mit.semantifyr.backends.theta.ThetaExecutorKey
import hu.bme.mit.semantifyr.verifier.assumeBackendAvailable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.io.File
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.Path
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.isExecutable
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText

class DistributionTest {

    companion object {
        private val home = Path("build/unzipped").listDirectoryEntries().single().toAbsolutePath()
        private val repository = Path("../..").toAbsolutePath().normalize()
        private val testModels = repository.resolve("oxsts-test-models")
        private val osName = System.getProperty("os.name")
        private val windows = osName.startsWith("Windows")

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
    private fun run(launcher: String, vararg arguments: String): Run {
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
        return Run(process.exitValue(), stdout.readText(), stderr.readText())
    }

    private fun reportOf(run: Run): Path {
        val line = Json.parseToJsonElement(run.stdout.trim()).jsonObject
        return Path(line.getValue("report").jsonPrimitive.content)
    }

    @ParameterizedTest
    @ValueSource(strings = ["semantifyr", "semantifyr-sysml", "semantifyr-gamma"])
    fun `each launcher prints its version without JVM warnings`(launcher: String) {
        val run = run(launcher, "--version")

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
    fun `the bundled smoke model passes with the bundled Theta`() {
        val run = run("semantifyr", "verify", "-q", "--theta-executor", "shell", "--portfolio", "theta-cegar-expl-pred-combined", "-d", "artifacts", home.resolve("models/smoke").toString())

        assertThat(run.exitCode).describedAs(run.stderr).isZero()
        assertThat(run.stdout.trim()).isEqualTo("PASSED")
        assertThat(run.stderr).doesNotContain("WARNING")
    }

    @Test
    fun `a violated property exits with the failed code and names its report`() {
        val run = run(
            "semantifyr", "verify", "--json", "--theta-executor", "shell", "--portfolio", "theta-cegar-expl-pred-combined", "-d", "artifacts",
            "--case-name", "semantifyr::verification::lang::local_vars::LocalVarUninitializedBound",
            testModels.resolve("semantic/local_vars.oxsts").toString(),
        )

        assertThat(run.exitCode).describedAs(run.stderr).isEqualTo(5)
        val line = Json.parseToJsonElement(run.stdout.trim()).jsonObject
        assertThat(line.getValue("exitCode").jsonPrimitive.int).isEqualTo(5)
        val report = reportOf(run)
        val result = Json.parseToJsonElement(report.readText()).jsonObject.getValue("result").jsonObject
        assertThat(result.getValue("verdict").jsonPrimitive.content).isEqualTo("Failed")
        assertThat(report.resolveSibling("witness.json")).exists()
    }

    @Test
    fun `the process exits with the usage and input codes`() {
        assertThat(run("semantifyr", "verify", "--portfolio", "no-such-portfolio", "models").exitCode).isEqualTo(2)
        assertThat(run("semantifyr", "list", directory.resolve("absent.oxsts").toString()).exitCode).isEqualTo(3)
        assertThat(run("semantifyr-sysml", "verify", directory.resolve("absent.sysml").toString()).exitCode).isEqualTo(3)
        assertThat(run("semantifyr-gamma", "verify", directory.resolve("Absent.gamma").toString()).exitCode).isEqualTo(3)
    }

    @Test
    fun `the log level option sets the level of the log`() {
        val smoke = home.resolve("models/smoke").toString()

        val quiet = run("semantifyr", "list", smoke)
        val verbose = run("semantifyr", "--log-level", "info", "list", smoke)

        assertThat(quiet.exitCode).describedAs(quiet.stderr).isZero()
        assertThat(quiet.stderr).doesNotContain("[INFO ]")
        assertThat(verbose.exitCode).describedAs(verbose.stderr).isZero()
        assertThat(verbose.stderr).contains("[INFO ]").contains("list model=")
        assertThat(verbose.stdout).isEqualTo(quiet.stdout)
    }

    @Test
    fun `the SysML launcher verifies a SysML model with the bundled Theta and writes its trace`() {
        val node = runCatching {
            ProcessBuilder("node", "--version").redirectErrorStream(true).start().waitFor() == 0
        }.getOrDefault(false)
        assumeTrue(node, "translating SysML v2 needs node on PATH")
        val model = repository.resolve("subprojects/frontends/sysml/models/tutorial/room.sysml")
            .copyTo(directory.resolve("model").createDirectories().resolve("room.sysml"))

        val run = run(
            "semantifyr-sysml", "verify", "--json", "--theta-executor", "shell", "--portfolio", "theta-bounded-bmc", "-d", "artifacts",
            "--case-name", "semantifyr::sysml::model::LampLitOnlyWhileSwitchOnViolated",
            model.toString(),
        )

        assertThat(run.exitCode).describedAs(run.stderr).isEqualTo(5)
        assertThat(reportOf(run).resolveSibling("trace.sysml")).exists()
    }

    @Test
    fun `the Gamma launcher verifies a Gamma model with the bundled Theta and writes its trace`() {
        val run = run(
            "semantifyr-gamma", "verify", "--json", "--theta-executor", "shell", "--portfolio", "theta-bounded-bmc", "-d", "artifacts",
            "--case-name", "Simple.WorkerStatechartOperationalReachable",
            repository.resolve("subprojects/frontends/gamma/models/examples/Simple.gamma").toString(),
        )

        assertThat(run.exitCode).describedAs(run.stderr).isZero()
        assertThat(reportOf(run).resolveSibling("trace.gamma")).exists()
    }
}
