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

val sysmlCommit = "fdca597594452bca3bd6f31a2210c35a2aefe1a0" // Updated extension name
val sysmlUrl = "https://github.com/arminzavada/sysml-2ls.git"
val sysmlDir = layout.projectDirectory.dir("sysml-2ls")

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

val cleanStaleVsix by tasks.registering(Delete::class) {
    delete(fileTree(sysmlDir.dir("packages/syside-vscode")) { include("*.vsix") })
}

val bundleExtension by tasks.registering(PnpmTask::class) {
    dependsOn(tasks.pnpmInstall)
    dependsOn(cleanStaleVsix)
    inputs.file(sysmlDir.file("package.json"))
    inputs.file(sysmlDir.file("tsconfig.json"))
    inputs.files(sysmlSourceFiles)
    outputs.files(fileTree(sysmlDir.dir("packages/syside-vscode")) { include("*.vsix") })

    pnpmCommand.set(listOf("run", "vscode:package"))
}

val sysideVsix = layout.buildDirectory.file("vscode-extension/syside-vscode.vsix")

val collectExtension by tasks.registering(Sync::class) {
    from(bundleExtension)
    rename(".*\\.vsix", "syside-vscode.vsix")
    into(sysideVsix.map { it.asFile.parentFile })
}

val buildCli by tasks.registering(PnpmTask::class) {
    dependsOn(tasks.pnpmInstall)
    mustRunAfter(bundleExtension) // input fileTree at packages/ overlaps bundleExtension's output dir; logically independent
    inputs.file(sysmlDir.file("package.json"))
    inputs.file(sysmlDir.file("tsconfig.json"))
    inputs.files(sysmlSourceFiles)
    outputs.file(sysmlDir.file("packages/syside-cli/dist/cli.cjs"))

    pnpmCommand.set(listOf("run", "cli"))
}

val bundleCli by tasks.registering(Sync::class) {
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
    add(distributionOutput.name, sysideVsix) {
        builtBy(collectExtension)
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
