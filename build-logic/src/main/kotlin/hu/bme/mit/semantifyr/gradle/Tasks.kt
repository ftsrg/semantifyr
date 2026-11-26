/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.gradle

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.provider.Provider

// a task without outputs is never up to date, so checks that write no files get a stamp
fun Task.stampOnSuccess() {
    val stamp = project.layout.buildDirectory.file("$name.stamp")
    outputs.file(stamp)
    doLast {
        stamp.get().asFile.writeText("")
    }
}

fun Project.commitHash(): Provider<String> {
    return providers.exec {
        commandLine("git", "rev-parse", "--short", "HEAD")
        isIgnoreExitValue = true
    }.standardOutput.asText.map {
        it.trim()
    }
}
