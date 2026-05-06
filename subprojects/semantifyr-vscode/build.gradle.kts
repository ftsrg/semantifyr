/*
 * SPDX-FileCopyrightText: 2023-2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import com.github.gradle.node.npm.task.NpmTask

plugins {
    id("hu.bme.mit.semantifyr.gradle.conventions.frontend")
}

val distributionOutput by configurations.creating {
    isCanBeConsumed = true
    isCanBeResolved = false
}

val distributionClasspath by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

val editorCommonDist by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    distributionClasspath(project(":oxsts.lang.ide", configuration = "distributionOutput"))
    distributionClasspath(project(":gamma.lang.ide", configuration = "distributionOutput"))

    editorCommonDist(project(":semantifyr-editor-common", configuration = "distributionOutput"))
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

tasks.npmInstall {
    workingDir = rootProject.projectDir
}

val buildExtension by tasks.registering(NpmTask::class) {
    inputs.dir(project.layout.projectDirectory.dir("src"))
    inputs.file(project.layout.projectDirectory.file("esbuild.mjs"))
    inputs.file(project.layout.projectDirectory.file("eslint.config.js"))
    inputs.file(project.layout.projectDirectory.file("tsconfig.json"))
    inputs.file(rootProject.layout.projectDirectory.file("package.json"))
    inputs.file(rootProject.layout.projectDirectory.file("package-lock.json"))
    inputs.files(tasks.npmInstall)
    inputs.files(editorCommonDist)

    npmCommand.set(
        listOf(
            "run",
            "build",
        ),
    )

    outputs.dir("dist")
}

val syncPackageVersion by tasks.registering(NpmTask::class) {
    description = "Rewrite package.json version to match project.version"
    inputs.files(tasks.npmInstall)
    inputs.property("version", project.version.toString())
    outputs.file(project.layout.projectDirectory.file("package.json"))
    npmCommand.set(listOf("pkg", "set", "version=${project.version}"))
}

val vsixFile = project.layout.buildDirectory.file("semantifyr-${project.version}.vsix")

val bundleExtension by tasks.registering(NpmTask::class) {
    inputs.files(syncPackageVersion)

    inputs.files(cloneDistribution)
    inputs.dir(project.layout.projectDirectory.dir("src"))
    inputs.dir(project.layout.projectDirectory.dir("syntaxes"))
    inputs.file(project.layout.projectDirectory.file("esbuild.mjs"))
    inputs.file(project.layout.projectDirectory.file("eslint.config.js"))
    inputs.file(project.layout.projectDirectory.file("language-configuration.json"))
    inputs.file(project.layout.projectDirectory.file("tsconfig.json"))
    inputs.file(project.layout.projectDirectory.file("icons/icon.png"))
    inputs.file(project.layout.projectDirectory.file("icons/semantifyr-file-icon.png"))
    inputs.file(project.layout.projectDirectory.file("icons/gamma-file-icon.png"))
    inputs.file(project.layout.projectDirectory.file("README.md"))
    inputs.file(project.layout.projectDirectory.file("CHANGELOG.md"))
    inputs.file(rootProject.layout.projectDirectory.file("package.json"))
    inputs.file(rootProject.layout.projectDirectory.file("package-lock.json"))
    inputs.files(tasks.npmInstall)
    inputs.files(editorCommonDist)

    npmCommand.set(
        listOf(
            "run",
            "bundle",
        ),
    )

    outputs.file(vsixFile)
}

tasks {
    assemble {
        inputs.files(cloneDistribution)
        inputs.files(buildExtension)
    }

    clean {
        delete("dist")
    }
}

artifacts {
    add(distributionOutput.name, vsixFile) {
        builtBy(bundleExtension)
    }
}
