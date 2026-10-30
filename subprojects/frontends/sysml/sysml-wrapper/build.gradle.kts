/*
 * SPDX-FileCopyrightText: 2023-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import com.github.gradle.node.pnpm.task.PnpmTask
import com.github.gradle.node.task.NodeTask
import hu.bme.mit.semantifyr.gradle.conventions.semantifyrArtifact
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

val sysmlCommit = "7893df64d2f51ab60e6ecb1124bfb7a219d90bd9" // chore: bumped vscode to 1.109
val sysmlUrl = "https://github.com/arminzavada/sysml-2ls.git"
val sysmlDir = layout.projectDirectory.dir("sysml-2ls")

node {
    nodeProjectDir = sysmlDir
}

val checkoutSysml = tasks.register<Exec>("checkoutSysml") {
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
    // the checkout's working tree is not a declared output of checkoutSysml
    dependsOn(checkoutSysml)
    // every pnpm run records its run state here
    nodeModulesOutputFilter {
        exclude(".pnpm-task-run-state-v1")
    }
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

val buildCli = tasks.register<PnpmTask>("buildCli") {
    inputs.files(tasks.pnpmInstall)
    inputs.file(sysmlDir.file("package.json"))
    inputs.file(sysmlDir.file("tsconfig.json"))
    inputs.files(sysmlSourceFiles)
    outputs.file(sysmlDir.file("packages/syside-cli/dist/cli.cjs"))

    pnpmCommand.set(listOf("run", "cli"))
}

val bundleExtension = tasks.register<PnpmTask>("bundleExtension") {
    inputs.files(tasks.pnpmInstall)
    mustRunAfter(buildCli)
    inputs.file(sysmlDir.file("package.json"))
    inputs.file(sysmlDir.file("tsconfig.json"))
    inputs.files(sysmlSourceFiles)
    val vscodePackageDir = sysmlDir.dir("packages/syside-vscode")
    outputs.files(fileTree(vscodePackageDir) { include("*.vsix") })

    // the vsix name carries the fork's version, and collectExtension must find only the current one
    doFirst {
        vscodePackageDir.asFile.listFiles { file -> file.extension == "vsix" }?.forEach { it.delete() }
    }

    pnpmCommand.set(listOf("run", "vscode:package"))
}

val sysideVsix = layout.buildDirectory.file("vscode-extension/syside-vscode.vsix")

val collectExtension = tasks.register<Sync>("collectExtension") {
    from(bundleExtension)
    rename(".*\\.vsix", "syside-vscode.vsix")
    into(sysideVsix.map { it.asFile.parentFile })
}

val bundleCli = tasks.register<Sync>("bundleCli") {
    inputs.files(buildCli)
    from(sysmlDir.file("packages/syside-cli/dist/cli.cjs"))
    from(fileTree(sysmlDir.dir("SysML-v2-Release/sysml.library"))) {
        include("**/*.sysml")
        include("**/*.kerml")
        into("sysml.library")
    }
    into(project.layout.buildDirectory.dir("cli-bundle"))
}

configurations.consumable("vscodeExtensionElements") {
    attributes(semantifyrArtifact("vscode-extension"))
    outgoing.artifact(sysideVsix) {
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
    script = cliBundleDir.map { it.file("cli.cjs") }
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

val compileSysmlExamples = tasks.register("compileSysmlExamples") {
    inputs.files(compileSysmlExampleCompressedSpacecraft)
    inputs.files(compileSysmlExampleCrossroads)
    inputs.files(compileSysmlExampleDoorAccess)
    inputs.files(compileSysmlExampleOrionProtocol)
}

configurations.consumable("compiledExamplesElements") {
    attributes(semantifyrArtifact("compiled-examples"))
    outgoing.artifact(compiledExamplesDir) {
        builtBy(compileSysmlExamples)
    }
}
