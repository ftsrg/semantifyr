/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.gradle

import com.github.gradle.node.pnpm.task.PnpmTask
import com.github.gradle.node.task.BaseTask
import org.gradle.api.Project
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.PathSensitivity
import org.gradle.kotlin.dsl.assign
import org.gradle.kotlin.dsl.register
import org.gradle.language.base.plugins.LifecycleBasePlugin

fun BaseTask.readsWorkspace() {
    val root = project.isolated.rootProject.projectDirectory
    inputs.files(
        root.file(".nvmrc"),
        root.file("tsconfig.base.json"),
        root.file("package.json"),
        root.file("pnpm-workspace.yaml"),
        root.file("pnpm-lock.yaml"),
        project.artifactsOf("pnpm-workspace", ":"),
    ).withPathSensitivity(PathSensitivity.RELATIVE)
}

fun PnpmTask.readsPackage() {
    readsWorkspace()
    val packageDirectory = project.layout.projectDirectory
    inputs.dir(packageDirectory.dir("src")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(packageDirectory.file("package.json")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(packageDirectory.file("tsconfig.json")).withPathSensitivity(PathSensitivity.RELATIVE)
}

fun Project.registerPnpmCheck(ownInputs: PnpmTask.() -> Unit) {
    val pnpmCheck = tasks.register<PnpmTask>("pnpmCheck") {
        group = LifecycleBasePlugin.VERIFICATION_GROUP

        readsPackage()
        inputs.file(isolated.rootProject.projectDirectory.file("eslint.config.base.js")).withPathSensitivity(PathSensitivity.RELATIVE)
        inputs.files(
            fileTree(layout.projectDirectory) {
                include(
                    "eslint.config.*",
                    "vite*.config.*",
                )
            },
        ).withPathSensitivity(PathSensitivity.RELATIVE)
        ownInputs()

        pnpmCommand = listOf("run", "check")

        outputs.file(layout.buildDirectory.file("tsconfig.tsbuildinfo"))
        outputs.file(layout.buildDirectory.file("eslint.json"))
        outputs.file(layout.buildDirectory.file("test-results/unit.xml"))
        outputs.cacheIf {
            true
        }
    }

    tasks.named("check") {
        dependsOn(pnpmCheck)
    }
}

fun Project.pnpmVersionOf(manifest: RegularFile): Provider<String> {
    return providers.fileContents(manifest).asText.map {
        Regex("\"packageManager\"\\s*:\\s*\"pnpm@([^\"+]+)").find(it)?.groupValues?.get(1)
    }
}
