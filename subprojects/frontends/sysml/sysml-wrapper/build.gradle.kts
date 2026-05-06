/*
 * SPDX-FileCopyrightText: 2023-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import com.github.gradle.node.pnpm.task.PnpmTask
import com.github.gradle.node.task.NodeTask
import org.apache.tools.ant.taskdefs.condition.Os

plugins {
    id("hu.bme.mit.semantifyr.gradle.conventions.jvm")
    id("hu.bme.mit.semantifyr.gradle.conventions.frontend")
    kotlin("jvm")
}

dependencies {
    implementation(project(":utils"))
    implementation(project(":logging"))
}

val sysmlCommit = "e1d22922929d10a26885ff144b654fc247e45e56" // Use spawn instead of exec to inherit stdio
val sysmlUrl = "https://github.com/arminzavada/sysml-2ls.git"
val sysmlDir = layout.buildDirectory.dir("sysml-2ls").get()

node {
    nodeProjectDir = sysmlDir
}

val distributionOutput by configurations.creating {
    isCanBeConsumed = true
    isCanBeResolved = false
}

val checkoutSysml by tasks.registering(Exec::class) {
    inputs.property("sysmlUrl", sysmlUrl)
    inputs.property("sysmlCommit", sysmlCommit)
    inputs.dir("scripts")
    outputs.dir(sysmlDir.dir(".git"))

    if (Os.isFamily(Os.FAMILY_WINDOWS)) {
        commandLine("cmd.exe", "/d", "/c", "scripts\\checkout.cmd", sysmlDir.asFile.absolutePath, sysmlUrl, sysmlCommit)
    } else {
        commandLine("scripts/checkout.sh", sysmlDir.asFile.absolutePath, sysmlUrl, sysmlCommit)
    }
}

tasks.pnpmInstall {
    dependsOn(checkoutSysml)
}

val checkoutLibrary by tasks.registering(NodeTask::class) {
    dependsOn(tasks.pnpmInstall) // node_modules directory is not reliable
    inputs.file(sysmlDir.file("packages/syside-languageserver/scripts/clone-sysml-release.mjs"))
    outputs.dir(sysmlDir.dir("SysML-v2-Release"))

    script = sysmlDir.file("packages/syside-languageserver/scripts/clone-sysml-release.mjs")
}

val buildExtension by tasks.registering(PnpmTask::class) {
    dependsOn(tasks.pnpmInstall) // node_modules directory is not reliable
    inputs.file(sysmlDir.file("package.json"))
    inputs.file(sysmlDir.file("tsconfig.json"))
    inputs.file(sysmlDir.file("tsconfig.build.json"))
    inputs.file(sysmlDir.file("tsconfig.eslint.json"))
    inputs.files(
        fileTree(sysmlDir.dir("packages")) {
            include("**/src/**/*.ts")
            include("**/tsconfig.json")
            include("**/package.json")
            include("**/package-lock.json")
            include("**/scripts/*.*")
            include("**/scripts/*.*")
        },
    )
    outputs.files(
        fileTree(sysmlDir.dir("packages")) {
            exclude("**/node_modules/**")
            include("**/lib/**")
            include("**/dist/**")
        },
    )

    pnpmCommand.set(
        listOf(
            "run",
            "build",
        ),
    )
}

val bundleExtension by tasks.registering(PnpmTask::class) {
    inputs.files(buildExtension)
    inputs.file(sysmlDir.file("package.json"))
    inputs.file(sysmlDir.file("tsconfig.json"))
    inputs.file(sysmlDir.file("tsconfig.build.json"))
    inputs.file(sysmlDir.file("tsconfig.eslint.json"))
    inputs.files(
        fileTree(sysmlDir.dir("packages")) {
            include("**/src/**/*.ts")
            include("**/tsconfig.json")
            include("**/package.json")
            include("**/package-lock.json")
            include("**/scripts/*.*")
            include("**/scripts/*.*")
        },
    )
    outputs.file(sysmlDir.file("packages/syside-vscode/sysml-2ls-0.9.0.vsix"))

    pnpmCommand.set(
        listOf(
            "run",
            "vscode:package",
        ),
    )
}

val buildCli by tasks.registering(PnpmTask::class) {
    dependsOn(tasks.pnpmInstall)
    inputs.file(sysmlDir.file("package.json"))
    inputs.file(sysmlDir.file("tsconfig.json"))
    inputs.file(sysmlDir.file("tsconfig.build.json"))
    inputs.file(sysmlDir.file("tsconfig.eslint.json"))
    inputs.files(
        fileTree(sysmlDir.dir("packages")) {
            include("**/src/**/*.ts")
            include("**/tsconfig.json")
            include("**/package.json")
            include("**/package-lock.json")
            include("**/scripts/*.*")
            include("**/scripts/*.*")
        },
    )
    outputs.file(sysmlDir.file("packages/syside-cli/out/index.js"))

    workingDir = sysmlDir.dir("packages/syside-cli")

    pnpmCommand.set(
        listOf(
            "run",
            "esbuild",
        ),
    )
}

val bundleCli by tasks.registering(Sync::class) {
    inputs.files(checkoutLibrary)
    inputs.files(buildCli)
    from(sysmlDir.file("packages/syside-cli/out/index.js"))
    from(fileTree(sysmlDir.dir("SysML-v2-Release/sysml.library"))) {
        include("**/*.sysml")
        include("**/*.kerml")
        into("sysml.library")
    }
    into(project.layout.buildDirectory.dir("cli-bundle"))
}

artifacts {
    add(distributionOutput.name, bundleExtension.map { it.outputs.files.singleFile }) {
        builtBy(bundleExtension)
    }
}

tasks.processResources {
    from(bundleCli) {
        into("sysml-cli")
    }
}

val compiledExamplesDir = layout.buildDirectory.dir("compiled-examples")
val examplesSourceDir = layout.projectDirectory.dir("../models/examples")
val cliBundleDir = layout.buildDirectory.dir("cli-bundle")

fun sysmlExampleTask(name: String) = tasks.register<NodeTask>("compileSysmlExample_$name") {
    inputs.files(bundleCli)
    val sourceFile = examplesSourceDir.file("$name.sysml")
    val targetFile = compiledExamplesDir.map { it.file("$name.oxsts") }
    inputs.file(sourceFile).withPathSensitivity(PathSensitivity.NAME_ONLY)
    outputs.file(targetFile)
    outputs.cacheIf { true }
    script = cliBundleDir.get().file("index.js").asFile
    args = provider {
        listOf(
            "compile",
            sourceFile.asFile.absolutePath,
            cliBundleDir.get().dir("sysml.library").asFile.absolutePath,
            "-o",
            targetFile.get().asFile.absolutePath,
        )
    }
}

val compileSysmlExampleCompressedSpacecraft = sysmlExampleTask("compressedspacecraft")
val compileSysmlExampleCrossroads = sysmlExampleTask("crossroads")
val compileSysmlExampleDoorAccess = sysmlExampleTask("door_access")
val compileSysmlExampleOrionProtocol = sysmlExampleTask("orion_protocol")

val compileSysmlExamples by tasks.registering {
    inputs.files(compileSysmlExampleCompressedSpacecraft)
    inputs.files(compileSysmlExampleCrossroads)
    inputs.files(compileSysmlExampleDoorAccess)
    inputs.files(compileSysmlExampleOrionProtocol)
}

val compiledExamples by configurations.creating {
    isCanBeConsumed = true
    isCanBeResolved = false
}

artifacts {
    add(compiledExamples.name, compiledExamplesDir) {
        builtBy(compileSysmlExamples)
    }
}
