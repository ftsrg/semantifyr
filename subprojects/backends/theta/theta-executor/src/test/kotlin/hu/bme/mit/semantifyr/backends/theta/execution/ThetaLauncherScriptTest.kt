/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.theta.execution

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

class ThetaLauncherScriptTest {

    @TempDir
    lateinit var directory: Path

    private val javaHome by lazy {
        directory.resolve("jdk")
    }

    private val pathDirectory by lazy {
        directory.resolve("path")
    }

    @BeforeEach
    fun writeJavaStubs() {
        assumeFalse(System.getProperty("os.name").startsWith("Windows"), "the stubs are shell scripts")
        writeStub(javaHome.resolve("bin").createDirectories().resolve("java"), "JAVA_HOME java")
        writeStub(pathDirectory.createDirectories().resolve("java"), "PATH java")
    }

    private fun writeStub(file: Path, name: String) {
        file.writeText("#!/bin/sh\necho \"$name\"\n")
        file.toFile().setExecutable(true)
    }

    private fun runLauncher(javaHome: Path?): String {
        val processBuilder = ProcessBuilder(Path("scripts/theta-xsts-cli").toAbsolutePath().toString(), "--version")
            .redirectErrorStream(true)
        val environment = processBuilder.environment()
        environment["PATH"] = listOf(pathDirectory.toString(), "/usr/bin", "/bin").joinToString(File.pathSeparator)
        if (javaHome == null) {
            environment.remove("JAVA_HOME")
        } else {
            environment["JAVA_HOME"] = javaHome.toString()
        }
        val process = processBuilder.start()
        check(process.waitFor(1, TimeUnit.MINUTES)) {
            "theta-xsts-cli did not exit"
        }
        return process.inputStream.bufferedReader().readText()
    }

    @Test
    fun `the launcher runs the JVM of JAVA_HOME before the one on PATH`() {
        assertThat(runLauncher(javaHome)).isEqualTo("JAVA_HOME java\n")
    }

    @Test
    fun `without JAVA_HOME the launcher runs the JVM on PATH`() {
        assertThat(runLauncher(null)).isEqualTo("PATH java\n")
    }
}
