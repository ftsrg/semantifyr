/*
 * SPDX-FileCopyrightText: 2023-2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

rootProject.name = "semantifyr"

include(
    "semantics",
//    "semantifyr-cli",
    "oxsts.model",
    "oxsts.lang",
    "oxsts.lang.ide",
    "semantifyr-vscode",
)

rootProject.children.forEach { project ->
    project.projectDir = file("subprojects/${project.name}")
}

fun includeDirectory(dirPath: String) {
    val dir = file(dirPath)
    val projects = dir.listFiles()?.filter { it.isDirectory } ?: emptyList()

    for (subProject in projects) {
        include(subProject.name)
        project(":${subProject.name}").projectDir = subProject
    }
}

includeDirectory("subprojects/frontends/gamma")
//includeDirectory("subprojects/backends/theta")
