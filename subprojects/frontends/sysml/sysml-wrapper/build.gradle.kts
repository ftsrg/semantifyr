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
    id("hu.bme.mit.semantifyr.gradle.conventions.nodejs")
    kotlin("jvm")
}

dependencies {
    implementation(project(":utils"))
    implementation(project(":logging"))
}

val sysmlCommit = "e6c281c6801f9a367448caf325ad5caa49abab53" // bump after pushing the wrapper-compatible refactor
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

// The stdlib clone moved to the workspace root in sysml-2ls; the wrapper-side
// `pnpmInstall` already triggers it via the upstream `postinstall` hook, but
// we keep an explicit task so callers that want only the library don't need to
// install dev deps.
val checkoutLibrary by tasks.registering(NodeTask::class) {
    dependsOn(tasks.pnpmInstall)
    inputs.file(sysmlDir.file("scripts/clone-sysml-release.mjs"))
    outputs.dir(sysmlDir.dir("SysML-v2-Release"))

    script = sysmlDir.file("scripts/clone-sysml-release.mjs")
}

// Shared input set: TS sources, package manifests, helper scripts. The new
// build pipeline produces per-package `lib/` (tsc output) and `dist/` (esbuild
// bundles); both are checked in `outputs`.
val sysmlSourceFiles = fileTree(sysmlDir.dir("packages")) {
    include("**/src/**/*.ts")
    include("**/gen/**/*.ts")
    include("**/tsconfig.json")
    include("**/package.json")
    include("**/esbuild.mjs")
    include("**/scripts/*.*")
    exclude("**/node_modules/**")
    exclude("**/lib/**")
    exclude("**/dist/**")
}

val sysmlBuildOutputs = fileTree(sysmlDir.dir("packages")) {
    exclude("**/node_modules/**")
    include("**/lib/**")
    include("**/dist/**")
}

val buildExtension by tasks.registering(PnpmTask::class) {
    dependsOn(tasks.pnpmInstall)
    inputs.file(sysmlDir.file("package.json"))
    inputs.file(sysmlDir.file("tsconfig.json"))
    inputs.files(sysmlSourceFiles)
    outputs.files(sysmlBuildOutputs)

    pnpmCommand.set(listOf("run", "build"))
}

val bundleExtension by tasks.registering(PnpmTask::class) {
    inputs.files(buildExtension)
    inputs.file(sysmlDir.file("package.json"))
    inputs.file(sysmlDir.file("tsconfig.json"))
    inputs.files(sysmlSourceFiles)
    outputs.files(fileTree(sysmlDir.dir("packages/syside-vscode")) { include("*.vsix") })

    pnpmCommand.set(listOf("run", "vscode:package"))
}

// The CLI bundle is a single self-contained CommonJS file at
// `packages/syside-cli/dist/cli.cjs` (esbuild output, renamed from the
// legacy `out/index.js`).
val buildCli by tasks.registering(PnpmTask::class) {
    dependsOn(tasks.pnpmInstall)
    mustRunAfter(buildExtension)
    inputs.file(sysmlDir.file("package.json"))
    inputs.file(sysmlDir.file("tsconfig.json"))
    inputs.files(sysmlSourceFiles)
    outputs.file(sysmlDir.file("packages/syside-cli/dist/cli.cjs"))

    workingDir = sysmlDir.dir("packages/syside-cli").asFile

    pnpmCommand.set(listOf("run", "bundle"))
}

val bundleCli by tasks.registering(Sync::class) {
    inputs.files(checkoutLibrary)
    inputs.files(buildCli)
    from(sysmlDir.file("packages/syside-cli/dist/cli.cjs"))
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
    script = cliBundleDir.get().file("cli.cjs").asFile
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
