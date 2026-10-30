/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.nuxmv.transformation

import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration

data class NuxmvIVariable(
    val name: String,
    val smvType: String,
)

data class NuxmvFrozenVariable(
    val name: String,
    val smvType: String,
)

data class NuxmvPrimedDeclaration(
    val name: String,
    val variable: VariableDeclaration,
)

data class NuxmvBranch(
    val constraints: List<String> = emptyList(),
    val currentPrime: Map<VariableDeclaration, String> = emptyMap(),
    val newPrimes: List<NuxmvPrimedDeclaration> = emptyList(),
)

data class NuxmvBranchResult(
    val branch: NuxmvBranch,
    val inputVariables: List<NuxmvIVariable>,
    val frozenVariables: List<NuxmvFrozenVariable>,
)
