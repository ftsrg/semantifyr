/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.uppaal.ir

data class UppaalNta(
    val globalDeclarations: UppaalDeclarations,
    val templates: List<UppaalTemplate>,
    val systemDeclaration: String = "system Model;",
)

data class UppaalDeclarations(
    val typedefs: List<String> = emptyList(),
    val variables: List<UppaalVariableDecl> = emptyList(),
)

data class UppaalVariableDecl(
    val typeName: String,
    val name: String,
    val initialValue: String? = null,
)

data class UppaalTemplate(
    val name: String,
    val parameters: String = "",
    val localDeclarations: UppaalDeclarations = UppaalDeclarations(),
    val locations: List<UppaalLocation>,
    val initialLocationId: String,
    val edges: List<UppaalEdge>,
)

data class UppaalLocation(
    val id: String,
    val name: String,
    val kind: UppaalLocationKind = UppaalLocationKind.Normal,
    val invariant: String? = null,
)

enum class UppaalLocationKind {
    Normal,
    Committed,
    Urgent,
}

data class UppaalEdge(
    val sourceId: String,
    val targetId: String,
    val select: String? = null,
    val guard: String? = null,
    val sync: String? = null,
    val assignment: String? = null,
)
