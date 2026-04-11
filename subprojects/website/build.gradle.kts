/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import com.github.gradle.node.npm.task.NpmTask

plugins {
    base
    alias(libs.plugins.gradle.node)
}

node {
    version = "22.14.0"
    download = true
}

abstract class NpmService : BuildService<BuildServiceParameters.None>

val npmService = gradle.sharedServices.registerIfAbsent("npmService", NpmService::class.java) {
    maxParallelUsages.set(1)
}

tasks.withType<NpmTask>().configureEach {
    usesService(npmService)
}

val assembleFrontend by tasks.registering(NpmTask::class) {
    inputs.dir(project.layout.projectDirectory.dir("src"))
    inputs.dir(project.layout.projectDirectory.dir("static"))
    inputs.file(project.layout.projectDirectory.file("docusaurus.config.ts"))
    inputs.file(project.layout.projectDirectory.file("sidebars.ts"))
    inputs.file(project.layout.projectDirectory.file("tsconfig.json"))
    inputs.file(project.layout.projectDirectory.file("package.json"))
    inputs.file(project.layout.projectDirectory.file("package-lock.json"))
    inputs.files(tasks.npmInstall.get().outputs)

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
