/*
 * SPDX-FileCopyrightText: 2023-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import com.github.gradle.node.pnpm.task.PnpmTask
import kotlin.io.path.isSymbolicLink

plugins {
    base
    alias(libs.plugins.gradle.node)
}

val isCi: Boolean = System.getenv("CI") != null

node {
    version = "22.14.0"
    download = !isCi
    nodeProjectDir = project.layout.projectDirectory.dir("sysml-2ls")
}

val distributionOutput by configurations.creating {
    isCanBeConsumed = true
    isCanBeResolved = false
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
    outputs.file(
        fileTree("sysml-2ls") {
            include("packages/syside-vscode/*.vsix")
        }
    )

    pnpmCommand.set(
        listOf(
            "run",
            "vscode:package",
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

artifacts {
    add(distributionOutput.name, bundleExtension.get().outputs.files.singleFile) {
        builtBy(bundleExtension)
    }
}
