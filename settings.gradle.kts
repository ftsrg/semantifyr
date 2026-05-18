/*
 * SPDX-FileCopyrightText: 2023-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

rootProject.name = "semantifyr"

include(
    "logging",
    "utils",
    "guice-common",
    "compiler",
    "verifier",
    "portfolios",
    "backend",
    "lang-ide-common",
    "semantifyr-cli",
    "semantifyr-editor-common",
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

fun includeDirectory(directoryPath: String, namePrefix: String = "") {
    val directory = file(directoryPath)
    val projects = directory.listFiles()?.filter {
        it.isDirectory
    }?.filter {
        it.resolve("build.gradle.kts").isFile
    } ?: emptyList()

    for (subProject in projects) {
        val name = "$namePrefix${subProject.name}"
        include(name)
        project(":$name").projectDir = subProject
    }
}

includeDirectory("subprojects/semantifyr-live", namePrefix = "semantifyr-live-")

includeDirectory("subprojects/frontends/gamma")
includeDirectory("subprojects/frontends/sysml")

includeDirectory("subprojects/backends/theta")
includeDirectory("subprojects/backends/uppaal")
includeDirectory("subprojects/backends/nuxmv")
includeDirectory("subprojects/backends/spin")
