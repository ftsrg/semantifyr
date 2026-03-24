/*
 * SPDX-FileCopyrightText: 2023-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import com.github.gradle.node.pnpm.task.PnpmTask
import com.github.gradle.node.task.NodeTask
import org.apache.tools.ant.taskdefs.condition.Os

plugins {
    base
    alias(libs.plugins.gradle.node)
}

val sysmlCommit = "aecd217b0ebf5877c92fa3d16a79e98c73417b69"
val sysmlUrl = "git@github.com:arminzavada/sysml-2ls.git"
val sysmlDir = layout.buildDirectory.dir("sysml-2ls").get()

node {
    version = "22.14.0"
    download = true
    nodeProjectDir = sysmlDir
}

abstract class PnpmService : BuildService<BuildServiceParameters.None>
val pnpmService = gradle.sharedServices.registerIfAbsent("pnpmService", PnpmService::class.java) {
    maxParallelUsages.set(1)
}

// node tasks must not run in parallel, as pnpm is sensitive to that
tasks.withType<PnpmTask>().configureEach {
    usesService(pnpmService)
}
tasks.withType<NodeTask>().configureEach {
    usesService(pnpmService)
}

val distributionOutput by configurations.creating {
    isCanBeConsumed = true
    isCanBeResolved = false
}

val cliOutput by configurations.creating {
    isCanBeConsumed = true
    isCanBeResolved = false
}

val checkoutSysml by tasks.registering(Exec::class) {
    inputs.property("sysmlUrl", sysmlUrl)
    inputs.property("sysmlCommit", sysmlCommit)
    inputs.dir("scripts")
    outputs.dir(sysmlDir.dir(".git"))

    val script = if (Os.isFamily(Os.FAMILY_WINDOWS)) {
        "scripts\\checkout.cmd"
    } else {
        "scripts/checkout.sh"
    }

    commandLine(script, sysmlDir.asFile.absolutePath, sysmlUrl, sysmlCommit)
}

tasks.pnpmInstall {
    usesService(pnpmService)

    dependsOn(checkoutSysml)
}

val checkoutLibrary by tasks.registering(PnpmTask::class) {
    dependsOn(tasks.pnpmInstall) // node_modules directory is not reliable
    inputs.file(sysmlDir.file("packages/syside-languageserver/scripts/clone-sysml-release.mjs"))
    outputs.dir(sysmlDir.dir("SysML-v2-Release"))

    pnpmCommand = listOf(
        "run",
        "prepare-validation",
    )
}

val buildExtension by tasks.registering(PnpmTask::class) {
    dependsOn(tasks.pnpmInstall) // node_modules directory is not reliable
    inputs.file(sysmlDir.file("package.json"))
    inputs.file(sysmlDir.file("tsconfig.json"))
    inputs.file(sysmlDir.file("tsconfig.build.json"))
    inputs.file(sysmlDir.file("tsconfig.eslint.json"))
    inputs.files(fileTree(sysmlDir.dir("packages")) {
        include("**/src/**/*.ts")
        include("**/tsconfig.json")
        include("**/package.json")
        include("**/package-lock.json")
        include("**/scripts/*.*")
        include("**/scripts/*.*")
    })
    outputs.files(fileTree(sysmlDir.dir("packages")) {
        exclude("**/node_modules/**")
        include("**/lib/**")
        include("**/dist/**")
    })

    pnpmCommand.set(
        listOf(
            "run",
            "build",
        )
    )
}

val bundleExtension by tasks.registering(PnpmTask::class) {
    dependsOn(buildExtension)
    inputs.file(sysmlDir.file("package.json"))
    inputs.file(sysmlDir.file("tsconfig.json"))
    inputs.file(sysmlDir.file("tsconfig.build.json"))
    inputs.file(sysmlDir.file("tsconfig.eslint.json"))
    inputs.files(fileTree(sysmlDir.dir("packages")) {
        include("**/src/**/*.ts")
        include("**/tsconfig.json")
        include("**/package.json")
        include("**/package-lock.json")
        include("**/scripts/*.*")
        include("**/scripts/*.*")
    })
    outputs.file(sysmlDir.file("packages/syside-vscode/sysml-2ls-0.9.0.vsix"))

    pnpmCommand.set(
        listOf(
            "run",
            "vscode:package",
        )
    )
}

val buildCli by tasks.registering(PnpmTask::class) {
    dependsOn(tasks.pnpmInstall)
    inputs.file(sysmlDir.file("package.json"))
    inputs.file(sysmlDir.file("tsconfig.json"))
    inputs.file(sysmlDir.file("tsconfig.build.json"))
    inputs.file(sysmlDir.file("tsconfig.eslint.json"))
    inputs.files(fileTree(sysmlDir.dir("packages")) {
        include("**/src/**/*.ts")
        include("**/tsconfig.json")
        include("**/package.json")
        include("**/package-lock.json")
        include("**/scripts/*.*")
        include("**/scripts/*.*")
    })
    outputs.file(sysmlDir.file("packages/syside-cli/out/index.js"))

    workingDir = sysmlDir.dir("packages/syside-cli")

    pnpmCommand.set(
        listOf(
            "run",
            "esbuild",
        )
    )
}

val bundleCli by tasks.registering(Sync::class) {
    dependsOn(checkoutLibrary)
    dependsOn(buildCli)
    from(sysmlDir.file("packages/syside-cli/out/index.js"))
    from(fileTree(sysmlDir.dir("SysML-v2-Release/sysml.library"))) {
        include("**/*.sysml")
        include("**/*.kerml")
        into("sysml.library")
    }
    into(project.layout.buildDirectory.dir("cli-bundle"))
}

artifacts {
    add(distributionOutput.name, bundleExtension.get().outputs.files.singleFile) {
        builtBy(bundleExtension)
    }
    add(cliOutput.name, project.layout.buildDirectory.dir("cli-bundle").get().asFile) {
        builtBy(bundleCli)
    }
}
