/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.nuxmv.transformation

import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration

data class NuxmvIVar(
    val name: String,
    val typeSmv: String,
)

fun choiceIVar(
    name: String,
    branchCount: Int,
): NuxmvIVar {
    require(branchCount >= 2) { "choiceIVar branchCount must be >= 2 (got $branchCount)" }
    return NuxmvIVar(name = name, typeSmv = "0..${branchCount - 1}")
}

data class NuxmvPrimedDecl(
    val name: String,
    val variable: VariableDeclaration,
)

data class NuxmvBranch(
    val constraints: List<String> = emptyList(),
    val currentPrime: Map<VariableDeclaration, String> = emptyMap(),
    val newPrimes: List<NuxmvPrimedDecl> = emptyList(),
    val ivars: List<NuxmvIVar> = emptyList(),
)
