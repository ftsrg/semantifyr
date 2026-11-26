/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.gradle

import com.github.gradle.node.task.BaseTask
import org.gradle.api.tasks.PathSensitivity

fun BaseTask.readsWorkspace() {
    val root = project.isolated.rootProject.projectDirectory
    inputs.files(
        root.file("tsconfig.base.json"),
        root.file("eslint.config.base.js"),
        root.file("package.json"),
        root.file("package-lock.json"),
        project.artifactsOf("npm-workspace", ":"),
    ).withPathSensitivity(PathSensitivity.RELATIVE)
}
