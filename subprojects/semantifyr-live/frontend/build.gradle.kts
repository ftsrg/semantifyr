/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
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

val npmService = gradle.sharedServices.registerIfAbsent(
    "liveFrontendNpmService",
    NpmService::class.java,
) {
    maxParallelUsages.set(1)
}

tasks.withType<NpmTask>().configureEach {
    usesService(npmService)
}

val distributionOutput by configurations.creating {
    isCanBeConsumed = true
    isCanBeResolved = false
}

val assembleFrontend by tasks.registering(NpmTask::class) {
    group = "build"
    description = "Build the production SPA bundle into dist/"

    inputs.dir(project.layout.projectDirectory.dir("src"))
    inputs.file(project.layout.projectDirectory.file("index.html"))
    inputs.file(project.layout.projectDirectory.file("vite.config.ts"))
    inputs.file(project.layout.projectDirectory.file("tsconfig.json"))
    inputs.file(project.layout.projectDirectory.file("package.json"))
    inputs.file(project.layout.projectDirectory.file("package-lock.json"))
    inputs.files(tasks.npmInstall.get().outputs)

    npmCommand.set(listOf("run", "build"))
    outputs.dir(project.layout.projectDirectory.dir("dist"))
}

artifacts {
    add(distributionOutput.name, project.layout.projectDirectory.dir("dist")) {
        builtBy(assembleFrontend)
    }
}

val test by tasks.registering(NpmTask::class) {
    group = "verification"
    description = "Run the frontend Vitest unit + integration tests"
    dependsOn(tasks.npmInstall)
    inputs.dir(project.layout.projectDirectory.dir("src"))
    inputs.file(project.layout.projectDirectory.file("vite.config.ts"))
    inputs.file(project.layout.projectDirectory.file("tsconfig.json"))
    inputs.file(project.layout.projectDirectory.file("package.json"))
    inputs.file(project.layout.projectDirectory.file("package-lock.json"))
    npmCommand.set(listOf("test"))
}

tasks {
    assemble {
        dependsOn(assembleFrontend)
    }

    check {
        dependsOn(test)
    }

    clean {
        delete("dist")
    }
}
