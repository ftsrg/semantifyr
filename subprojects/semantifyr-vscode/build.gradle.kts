/*
 * SPDX-FileCopyrightText: 2023-2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import com.github.gradle.node.npm.task.NpmTask
import com.github.gradle.node.task.NodeTask

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

// node tasks must not run in parallel, as pnpm is sensitive to that
tasks.withType<NpmTask>().configureEach {
    usesService(npmService)
}
tasks.withType<NodeTask>().configureEach {
    usesService(npmService)
}

val distributionOutput by configurations.creating {
    isCanBeConsumed = true
    isCanBeResolved = false
}

val distributionClasspath by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    distributionClasspath(project(":oxsts.lang.ide", configuration = "distributionOutput"))
//    distributionClasspath(project(":semantifyr-cli", configuration = "distributionOutput"))
    distributionClasspath(project(":xsts.lang.ide", configuration = "distributionOutput"))
//    distributionClasspath(project(":cex.lang.ide", configuration = "distributionOutput"))
    distributionClasspath(project(":gamma.lang.ide", configuration = "distributionOutput"))
    distributionClasspath(project(":gamma-cli", configuration = "distributionOutput"))
}

val cloneDistribution by tasks.registering(Sync::class) {
    inputs.files(distributionClasspath)

    from(
        distributionClasspath.map {
            fileTree(it)
        },
    )

    into("bin")
}

val buildExtension by tasks.registering(NpmTask::class) {
    inputs.dir(project.layout.projectDirectory.dir("src"))
    inputs.file(project.layout.projectDirectory.file("esbuild.mjs"))
    inputs.file(project.layout.projectDirectory.file("eslint.config.js"))
    inputs.file(project.layout.projectDirectory.file("package-lock.json"))
    inputs.file(project.layout.projectDirectory.file("tsconfig.json"))
    inputs.files(tasks.npmInstall.get().outputs)

    npmCommand.set(
        listOf(
            "run",
            "build",
        ),
    )

    outputs.dir("dist")
}

val bundleExtension by tasks.registering(NpmTask::class) {
    inputs.files(cloneDistribution.get().outputs)
    inputs.dir(project.layout.projectDirectory.dir("src"))
    inputs.dir(project.layout.projectDirectory.dir("syntaxes"))
    inputs.file(project.layout.projectDirectory.file("esbuild.mjs"))
    inputs.file(project.layout.projectDirectory.file("eslint.config.js"))
    inputs.file(project.layout.projectDirectory.file("language-configuration.json"))
    inputs.file(project.layout.projectDirectory.file("package.json"))
    inputs.file(project.layout.projectDirectory.file("tsconfig.json"))
    inputs.files(tasks.npmInstall.get().outputs)

    npmCommand.set(
        listOf(
            "run",
            "bundle",
        ),
    )

    outputs.file(project.layout.buildDirectory.file("semantifyr-0.0.1.vsix"))
}

tasks {
    assemble {
        inputs.files(cloneDistribution.get().outputs)
        inputs.files(buildExtension.get().outputs)
    }

    clean {
        delete("dist")
    }
}

artifacts {
    add(distributionOutput.name, project.layout.buildDirectory.file("semantifyr-0.0.1.vsix")) {
        builtBy(bundleExtension)
    }
}
