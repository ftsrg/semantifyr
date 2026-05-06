/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import com.github.gradle.node.npm.task.NpmTask

plugins {
    id("hu.bme.mit.semantifyr.gradle.conventions.frontend")
}

tasks.npmInstall {
    workingDir = rootProject.projectDir
}

val distributionOutput by configurations.creating {
    isCanBeConsumed = true
    isCanBeResolved = false
}

val buildEditorCommon by tasks.registering(NpmTask::class) {
    workingDir = project.projectDir

    inputs.dir(project.layout.projectDirectory.dir("src"))
    inputs.file(project.layout.projectDirectory.file("tsconfig.json"))
    inputs.file(project.layout.projectDirectory.file("package.json"))
    inputs.file(rootProject.layout.projectDirectory.file("package.json"))
    inputs.file(rootProject.layout.projectDirectory.file("package-lock.json"))
    inputs.files(tasks.npmInstall)

    outputs.dir(project.layout.projectDirectory.dir("dist"))

    npmCommand.set(listOf("run", "build"))
}

artifacts {
    add(distributionOutput.name, project.layout.projectDirectory.dir("dist")) {
        builtBy(buildEditorCommon)
    }
}

tasks.assemble {
    dependsOn(buildEditorCommon)
}

tasks.clean {
    delete("dist", "tsconfig.tsbuildinfo")
}
