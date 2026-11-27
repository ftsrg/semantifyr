/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.gradle

import com.github.gradle.node.task.NodeTask
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.assign
import org.gradle.kotlin.dsl.register

fun Project.registerBrandingAssets(outputDirectory: Directory): TaskProvider<Sync> {
    val brandingDirectory = isolated.rootProject.projectDirectory.dir("branding")
    val renderBrandingPictures = registerRenderPictures(
        "renderBrandingPictures",
        layout.buildDirectory.dir("branding"),
        listOf(
            "favicon.ico=logo@16,32,48",
            "apple-touch-icon.png=logo@180",
            "logo-192.png=logo@192",
            "logo-512.png=logo@512",
            "social-card.png=social-card@1200",
        ),
    )

    return tasks.register<Sync>("cloneBrandingAssets") {
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
    outputDirectory: Provider<Directory>,
    specifications: List<String>,
): TaskProvider<NodeTask> {
    val brandingDirectory = isolated.rootProject.projectDirectory.dir("branding")
    val packageDirectory = projectDir

    return tasks.register<NodeTask>(taskName) {
        inputs.files(
            brandingDirectory.asFileTree.matching {
                include("*.svg")
            },
        ).withPathSensitivity(PathSensitivity.RELATIVE)
        readsWorkspace()
        outputs.dir(outputDirectory)
        outputs.cacheIf {
            true
        }

        script = brandingDirectory.file("render.mjs")
        args = outputDirectory.map {
            listOf(it.asFile.relativeTo(packageDirectory).path) + specifications
        }
    }
}
