/*
 * SPDX-FileCopyrightText: 2023-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import com.github.gradle.node.pnpm.task.PnpmTask

plugins {
    base
    alias(libs.plugins.gradle.node)
}

val sysml2lsCommit = "e9d675777390deabae2e620722288a2177271ab6"
val sysml2lsUrl = "git@github.com:arminzavada/sysml-2ls.git"
val sysml2lsDir = layout.projectDirectory.dir("sysml-2ls").asFile

val checkoutSysml2ls by tasks.registering(Exec::class) {
    inputs.property("sysml2lsUrl", sysml2lsUrl)
    inputs.property("sysml2lsCommit", sysml2lsCommit)
    outputs.dir(layout.projectDirectory.dir("sysml-2ls").dir(".git"))

    val isWindows = System.getProperty("os.name").lowercase().contains("win")
    val script = if (isWindows) "scripts\\checkout.cmd" else "scripts/checkout.sh"
    commandLine(script, sysml2lsDir.absolutePath, sysml2lsUrl, sysml2lsCommit)
}

node {
    version = "22.14.0"
    download = true
    nodeProjectDir = project.layout.projectDirectory.dir("sysml-2ls")
}

val distributionOutput by configurations.creating {
    isCanBeConsumed = true
    isCanBeResolved = false
}

val cliOutput by configurations.creating {
    isCanBeConsumed = true
    isCanBeResolved = false
}

tasks.pnpmInstall {
    dependsOn(checkoutSysml2ls)
}

val buildExtension by tasks.registering(PnpmTask::class) {
    dependsOn(tasks.pnpmInstall) // node_modules directory is not reliable
    inputs.files(fileTree("sysml-2ls") {
        include("**/src/**/*.ts")
        include("**/tsconfig.json")
        include("**/package.json")
        include("**/package-lock.json")
        include("**/scripts/*.*")
        include("**/scripts/*.*")
    })
    outputs.files(fileTree("sysml-2ls") {
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
    outputs.file("sysml-2ls/packages/syside-vscode/sysml-2ls-0.9.0.vsix")

    pnpmCommand.set(
        listOf(
            "run",
            "vscode:package",
        )
    )
}

val bundleCli by tasks.registering(PnpmTask::class) {
    // not needed, but it would be difficult to exclude the outputs of building the extension
    dependsOn(buildExtension)
    dependsOn(tasks.pnpmInstall)
    inputs.files(fileTree("sysml-2ls") {
        include("**/src/**/*.ts")
        include("**/tsconfig.json")
        include("**/package.json")
        include("**/package-lock.json")
        include("**/scripts/*.*")
        include("**/scripts/*.*")
    })
    outputs.file("sysml-2ls/packages/syside-cli/out/index.js")

    workingDir = project.layout.projectDirectory.dir("sysml-2ls/packages/syside-cli")

    pnpmCommand.set(
        listOf(
            "run",
            "esbuild",
        )
    )
}

tasks {
    assemble {
        inputs.files(buildExtension.get().outputs)
    }

    clean {
        delete("dist")
    }
}

val assembleCliBundle by tasks.registering(Sync::class) {
    dependsOn(bundleCli)
    from(file("sysml-2ls/packages/syside-cli/out/index.js"))
    from(project.layout.projectDirectory.dir("sysml-2ls/packages/syside-vscode/sysml.library")) {
        into("sysml.library")
    }
    into(project.layout.buildDirectory.dir("cli-bundle"))
}

artifacts {
    add(distributionOutput.name, bundleExtension.get().outputs.files.singleFile) {
        builtBy(bundleExtension)
    }
    add(cliOutput.name, project.layout.buildDirectory.dir("cli-bundle").get().asFile) {
        builtBy(assembleCliBundle)
    }
}
