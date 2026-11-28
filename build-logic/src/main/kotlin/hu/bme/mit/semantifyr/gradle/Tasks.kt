/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.gradle

import com.github.gradle.node.pnpm.task.PnpmTask
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.PathSensitivity
import org.gradle.process.ProcessForkOptions
import java.io.File

fun <T> T.runsWithTheta() where T : Task, T : ProcessForkOptions {
    val thetaDirectory = project.artifactsOf("theta-xsts-cli", ":theta-executor")
    inputs.files(thetaDirectory).withPathSensitivity(PathSensitivity.RELATIVE)
    doFirst {
        prependToPath(thetaDirectory.singleFile)
    }
}

fun PnpmTask.runsWithTheta() {
    val thetaDirectory = project.artifactsOf("theta-xsts-cli", ":theta-executor")
    inputs.files(thetaDirectory).withPathSensitivity(PathSensitivity.RELATIVE)
    environment.putAll(
        thetaDirectory.elements.map { directories ->
            val variables = System.getenv()
            val pathVariable = pathVariableOf(variables)
            mapOf(pathVariable to "${directories.single().asFile.absolutePath}${File.pathSeparator}${variables[pathVariable]}")
        },
    )
}

fun ProcessForkOptions.prependToPath(directory: File) {
    val pathVariable = pathVariableOf(environment)
    environment(pathVariable, "${directory.absolutePath}${File.pathSeparator}${environment[pathVariable]}")
}

private fun pathVariableOf(variables: Map<String, *>): String {
    return variables.keys.firstOrNull {
        it.equals("PATH", ignoreCase = true)
    } ?: "PATH"
}

fun Project.commitHash(): Provider<String> {
    return providers.exec {
        commandLine("git", "rev-parse", "--short", "HEAD")
        isIgnoreExitValue = true
    }.standardOutput.asText.map {
        it.trim()
    }
}
