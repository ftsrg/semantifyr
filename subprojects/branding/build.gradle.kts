/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import com.github.gradle.node.task.NodeTask
import hu.bme.mit.semantifyr.gradle.readsWorkspace
import hu.bme.mit.semantifyr.gradle.semantifyrArtifact

plugins {
    id("hu.bme.mit.semantifyr.nodejs")
}

val pictures = fileTree(layout.projectDirectory) {
    include("*.svg")
}

fun registerRender(
    name: String,
    outputDirectory: Provider<Directory>,
    vararg specifications: String,
): TaskProvider<NodeTask> {
    return tasks.register<NodeTask>(name) {
        inputs.files(pictures).withPathSensitivity(PathSensitivity.RELATIVE)
        readsWorkspace()
        outputs.dir(outputDirectory)
        outputs.cacheIf {
            true
        }

        script = layout.projectDirectory.file("render.mjs")
        args = outputDirectory.map {
            listOf(it.asFile.relativeTo(projectDir).path) + specifications
        }
    }
}

val renderWebPictures = registerRender(
    "renderWebPictures",
    layout.buildDirectory.dir("rendered-web"),
    "favicon.ico=logo@16,32,48",
    "apple-touch-icon.png=logo@180",
    "logo-192.png=logo@192",
    "logo-512.png=logo@512",
    "social-card.png=social-card@1200",
)

val webAssetsDirectory = layout.buildDirectory.dir("web")

val collectWebAssets = tasks.register<Sync>("collectWebAssets") {
    from(renderWebPictures)
    from(layout.projectDirectory) {
        include(
            "logo.svg",
            "logo-full-light.svg",
            "logo-full-dark.svg",
        )
    }
    into(webAssetsDirectory)
}

val vscodeIconsDirectory = layout.buildDirectory.dir("vscode-icons")

val renderVscodeIcons = registerRender(
    "renderVscodeIcons",
    vscodeIconsDirectory,
    "icon.png=logo@128",
    "semantifyr-file-icon.png=logo@128+104",
    "gamma-file-icon.png=gamma@128+104",
)

configurations.consumable("webBrandingElements") {
    attributes(semantifyrArtifact("web-branding"))
    outgoing.artifact(webAssetsDirectory) {
        builtBy(collectWebAssets)
    }
}

configurations.consumable("vscodeIconsElements") {
    attributes(semantifyrArtifact("vscode-icons"))
    outgoing.artifact(vscodeIconsDirectory) {
        builtBy(renderVscodeIcons)
    }
}
