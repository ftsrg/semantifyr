/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.corpuscheck

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.nameWithoutExtension

class LauncherRun(
    val exitCode: Int,
    val stdout: String,
)

fun interface Launcher {
    fun verify(model: Path, caseNames: List<String>): LauncherRun
}

class ProcessLauncher(
    private val executable: Path,
    private val artifactRoot: Path,
    private val timeoutSeconds: Long?,
) : Launcher {
    override fun verify(model: Path, caseNames: List<String>): LauncherRun {
        val artifactDirectory = Files.createTempDirectory(artifactRoot, "${model.nameWithoutExtension}-")
        val process = ProcessBuilder(command(model, caseNames, artifactDirectory))
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .start()
        val stdout = process.inputStream.bufferedReader().use {
            it.readText()
        }
        return LauncherRun(process.waitFor(), stdout)
    }

    internal fun command(
        model: Path,
        caseNames: List<String>,
        artifactDirectory: Path,
    ): List<String> {
        val command = mutableListOf(executable.toString(), "verify", "--json", "-d", artifactDirectory.toString())
        if (timeoutSeconds != null) {
            command += listOf("--timeout-seconds", timeoutSeconds.toString())
        }
        for (caseName in caseNames) {
            command += listOf("--case-name", caseName)
        }
        command += model.toString()
        return command
    }
}
