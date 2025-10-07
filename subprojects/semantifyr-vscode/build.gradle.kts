/*
 * SPDX-FileCopyrightText: 2023-2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import com.github.gradle.node.npm.task.NpmTask

plugins {
    base
    alias(libs.plugins.gradle.node)
}

val isCi: Boolean = System.getenv("CI") != null

node {
    version = "22.14.0"
    download = !isCi
}

val distributionClasspath by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    distributionClasspath(project(":oxsts.lang.ide", configuration = "distributionOutput"))
//    distributionClasspath(project(":semantifyr-cli", configuration = "distributionOutput"))
//    distributionClasspath(project(":xsts.lang.ide", configuration = "distributionOutput"))
//    distributionClasspath(project(":cex.lang.ide", configuration = "distributionOutput"))
    distributionClasspath(project(":gamma.lang.ide", configuration = "distributionOutput"))
    distributionClasspath(project(":gamma-cli", configuration = "distributionOutput"))
}

val cloneDistribution by tasks.registering(Sync::class) {
    inputs.files(distributionClasspath)

    from (distributionClasspath.map {
        fileTree(it)
    })

    into("bin")
}

tasks {
    val buildExtension by registering(NpmTask::class) {
        inputs.dir(project.layout.projectDirectory.dir("src"))
        inputs.file(project.layout.projectDirectory.file("esbuild.mjs"))
        inputs.file(project.layout.projectDirectory.file("eslint.config.js"))
        inputs.file(project.layout.projectDirectory.file("package-lock.json"))
        inputs.file(project.layout.projectDirectory.file("tsconfig.json"))
        inputs.files(npmInstall.get().outputs)

        npmCommand.set(
            listOf(
                "run",
                "build",
            )
        )

        outputs.dir("dist")
    }

    val bundleExtension by registering(NpmTask::class) {
        inputs.files(cloneDistribution.get().outputs)
        inputs.dir(project.layout.projectDirectory.dir("src"))
        inputs.dir(project.layout.projectDirectory.dir("syntaxes"))
        inputs.file(project.layout.projectDirectory.file("esbuild.mjs"))
        inputs.file(project.layout.projectDirectory.file("eslint.config.js"))
        inputs.file(project.layout.projectDirectory.file("language-configuration.json"))
        inputs.file(project.layout.projectDirectory.file("package.json"))
        inputs.file(project.layout.projectDirectory.file("tsconfig.json"))
        inputs.files(npmInstall.get().outputs)

        npmCommand.set(
            listOf(
                "run",
                "bundle",
            )
        )

        outputs.dir(project.layout.buildDirectory.dir("vscode"))
    }

    assemble {
        inputs.files(cloneDistribution.get().outputs)
        inputs.files(buildExtension.get().outputs)
    }

    clean {
        delete("dist")
    }
}
