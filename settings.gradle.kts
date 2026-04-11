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
    "semantifyr-vscode-server",
    "website",
)

rootProject.children.forEach { project ->
    project.projectDir = file("subprojects/${project.name}")
}

include("semantifyr-live-backend")
project(":semantifyr-live-backend").projectDir = file("subprojects/semantifyr-live/backend")

include("semantifyr-live-frontend")
project(":semantifyr-live-frontend").projectDir = file("subprojects/semantifyr-live/frontend")

fun includeDirectory(dirPath: String) {
    val dir = file(dirPath)
    val projects = dir.listFiles()?.filter { it.isDirectory } ?: emptyList()

    for (subProject in projects) {
        include(subProject.name)
        project(":${subProject.name}").projectDir = subProject
    }
}

includeDirectory("subprojects/frontends/gamma")
includeDirectory("subprojects/frontends/sysmlv2")
includeDirectory("subprojects/backends/theta")
