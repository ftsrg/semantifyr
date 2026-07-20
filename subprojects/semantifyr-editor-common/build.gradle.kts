/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import com.github.gradle.node.npm.task.NpmTask

plugins {
    id("hu.bme.mit.semantifyr.gradle.conventions.nodejs")
}

tasks.npmInstall {
    workingDir = rootProject.projectDir
}

val distributionOutput by configurations.creating {
    isCanBeConsumed = true
    isCanBeResolved = false
}

fun NpmTask.configureSharedInputs() {
    inputs.dir(project.layout.projectDirectory.dir("src"))
    inputs.file(project.layout.projectDirectory.file("tsconfig.json"))
    inputs.file(project.layout.projectDirectory.file("eslint.config.js"))
    inputs.file(project.layout.projectDirectory.file("package.json"))
    inputs.file(rootProject.layout.projectDirectory.file("tsconfig.base.json"))
    inputs.file(rootProject.layout.projectDirectory.file("eslint.config.base.js"))
    inputs.file(rootProject.layout.projectDirectory.file("package.json"))
    inputs.file(rootProject.layout.projectDirectory.file("package-lock.json"))
    inputs.files(tasks.npmInstall)
}

val npmAssemble by tasks.registering(NpmTask::class) {
    configureSharedInputs()

    outputs.dir(project.layout.projectDirectory.dir("dist"))

    npmCommand.set(listOf("run", "assemble"))
}

val npmCheck by tasks.registering(NpmTask::class) {
    configureSharedInputs()

    npmCommand.set(listOf("run", "check"))
}

artifacts {
    add(distributionOutput.name, project.layout.projectDirectory.dir("dist")) {
        builtBy(npmAssemble)
    }
}

tasks.assemble {
    dependsOn(npmAssemble)
}

tasks.check {
    dependsOn(npmCheck)
}

tasks.clean {
    delete("dist", "tsconfig.tsbuildinfo")
}
