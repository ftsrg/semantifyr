/*
 * SPDX-FileCopyrightText: 2023-2024 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

rootProject.name = "semantifyr"

include(
    "compiler",
    "xsts.lang",
    "xsts.lang.ide",
    "oxsts.model",
    "oxsts.lang",
    "oxsts.lang.ide",
    "semantifyr-vscode",
)

rootProject.children.forEach { project ->
    project.projectDir = file("subprojects/${project.name}")
}
