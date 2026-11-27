/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.gradle

import com.github.gradle.node.npm.task.NpmTask
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
        environment("PATH", "${thetaDirectory.singleFile.absolutePath}${File.pathSeparator}${System.getenv("PATH")}")
    }
}

fun NpmTask.runsWithTheta() {
    val thetaDirectory = project.artifactsOf("theta-xsts-cli", ":theta-executor")
    inputs.files(thetaDirectory).withPathSensitivity(PathSensitivity.RELATIVE)
    environment.put(
        "PATH",
        thetaDirectory.elements.zip(providers.environmentVariable("PATH")) { directories, path ->
            "${directories.single().asFile.absolutePath}${File.pathSeparator}$path"
        },
    )
}

fun Project.commitHash(): Provider<String> {
    return providers.exec {
        commandLine("git", "rev-parse", "--short", "HEAD")
        isIgnoreExitValue = true
    }.standardOutput.asText.map {
        it.trim()
    }
}
