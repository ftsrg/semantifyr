/*
 * SPDX-FileCopyrightText: 2023-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

pluginManagement {
    includeBuild("build-logic")
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        exclusiveContent {
            forRepository {
                ivy {
                    name = "Theta GitHub Releases"
                    url = uri("https://github.com/ftsrg/theta/releases/download/")
                    patternLayout {
                        artifact("v[revision]/[artifact].[ext]")
                    }
                    metadataSources { artifact() }
                }
            }
            filter { includeGroup("hu.bme.mit.ftsrg.theta") }
        }
        exclusiveContent {
            forRepository {
                ivy {
                    name = "Theta GitHub Raw"
                    url = uri("https://raw.githubusercontent.com/ftsrg/theta/")
                    patternLayout {
                        artifact("v[revision]/lib/[artifact].[ext]")
                    }
                    metadataSources { artifact() }
                }
            }
            filter { includeGroup("hu.bme.mit.ftsrg.theta.lib") }
        }
        exclusiveContent {
            forRepository {
                ivy {
                    name = "Node.js"
                    url = uri("https://nodejs.org/dist/")
                    patternLayout {
                        artifact("v[revision]/[artifact](-v[revision]-[classifier]).[ext]")
                    }
                    metadataSources { artifact() }
                }
            }
            filter { includeModule("org.nodejs", "node") }
        }
        mavenCentral()
    }
}

rootProject.name = "semantifyr"

include(
    "logging",
    "utils",
    "guice-common",
    "compiler",
    "verifier",
    "portfolios",
    "benchmark",
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
