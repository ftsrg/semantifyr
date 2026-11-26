/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.gradle.conventions

import com.github.gradle.node.task.NodeTask
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.TaskProvider

fun Project.registerBrandingAssets(outputDirectory: Directory): TaskProvider<Sync> {
    val brandingDirectory = rootProject.layout.projectDirectory.dir("branding")
    val renderBrandingPictures = registerRenderPictures(
        "renderBrandingPictures",
        layout.buildDirectory.dir("branding").get(),
        listOf(
            "favicon.ico=logo@16,32,48",
            "apple-touch-icon.png=logo@180",
            "logo-192.png=logo@192",
            "logo-512.png=logo@512",
            "social-card.png=social-card@1200",
        ),
    )

    return tasks.register("cloneBrandingAssets", Sync::class.java) {
        from(renderBrandingPictures)
        from(brandingDirectory) {
            include(
                "logo.svg",
                "logo-full-light.svg",
                "logo-full-dark.svg",
            )
        }
        into(outputDirectory)
    }
}

fun Project.registerRenderPictures(
    taskName: String,
    outputDirectory: Directory,
    specifications: List<String>,
): TaskProvider<NodeTask> {
    val brandingDirectory = rootProject.layout.projectDirectory.dir("branding")
    val renderScript = brandingDirectory.file("render.mjs")

    return tasks.register(taskName, NodeTask::class.java) {
        inputs.files(
            brandingDirectory.asFileTree.matching {
                include("*.svg")
            },
        ).withPathSensitivity(PathSensitivity.RELATIVE)
        inputs.file(renderScript).withPathSensitivity(PathSensitivity.NONE)
        inputs.file(rootProject.layout.projectDirectory.file("package-lock.json")).withPathSensitivity(PathSensitivity.NONE)
        dependsOn(tasks.named("npmInstall"))
        outputs.dir(outputDirectory)
        outputs.cacheIf {
            true
        }

        workingDir.set(projectDir)
        script.set(renderScript.asFile)
        args.set(listOf(projectDir.toPath().relativize(outputDirectory.asFile.toPath()).toString()) + specifications)
    }
}
