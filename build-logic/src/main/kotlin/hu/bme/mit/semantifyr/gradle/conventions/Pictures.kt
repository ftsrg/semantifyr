/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.gradle.conventions

import com.github.gradle.node.task.NodeTask
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.tasks.TaskProvider

fun Project.registerRenderPictures(
    taskName: String,
    outputDirectory: Directory,
    specifications: List<String>,
): TaskProvider<NodeTask> {
    val brandingDirectory = rootProject.layout.projectDirectory.dir("branding")
    val renderScript = brandingDirectory.file("render.mjs")

    return tasks.register(taskName, NodeTask::class.java) {
        inputs.files(brandingDirectory.asFileTree.matching {
            include("*.svg")
        })
        inputs.file(renderScript)
        inputs.file(rootProject.layout.projectDirectory.file("package-lock.json"))
        inputs.files(tasks.named("npmInstall"))
        inputs.property("specifications", specifications)
        outputs.dir(outputDirectory)

        script.set(renderScript.asFile)
        args.set(listOf(outputDirectory.asFile.absolutePath) + specifications)
    }
}
