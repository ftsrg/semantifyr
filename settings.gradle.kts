/*
 * SPDX-FileCopyrightText: 2023-2024 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

rootProject.name = "semantifyr"

include(
    "compiler",
    "oxsts.model",
    "oxsts.lang",
    "oxsts.lang.ide",
    "oxsts-vs-code",
)

rootProject.children.forEach { project ->
    project.projectDir = file("subprojects/${project.name}")
}
