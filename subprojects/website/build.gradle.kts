/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import com.github.gradle.node.npm.task.NpmTask

plugins {
    id("hu.bme.mit.semantifyr.gradle.conventions.frontend")
}

val assembleFrontend by tasks.registering(NpmTask::class) {
    inputs.dir(project.layout.projectDirectory.dir("src"))
    inputs.dir(project.layout.projectDirectory.dir("static"))
    inputs.file(project.layout.projectDirectory.file("docusaurus.config.ts"))
    inputs.file(project.layout.projectDirectory.file("sidebars.ts"))
    inputs.file(project.layout.projectDirectory.file("tsconfig.json"))
    inputs.file(project.layout.projectDirectory.file("package.json"))
    inputs.file(project.layout.projectDirectory.file("package-lock.json"))
    inputs.files(tasks.npmInstall)

    npmCommand.set(listOf("run", "build"))
    outputs.dir(project.layout.buildDirectory.dir("docusaurus"))
}

tasks {
    assemble {
        dependsOn(assembleFrontend)
    }

    clean {
        delete(".docusaurus")
        delete("build")
    }
}
