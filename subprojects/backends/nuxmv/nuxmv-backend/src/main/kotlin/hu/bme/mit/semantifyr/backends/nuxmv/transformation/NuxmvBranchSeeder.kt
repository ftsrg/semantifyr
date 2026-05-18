/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.nuxmv.transformation

import com.google.inject.Inject
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration

class NuxmvBranchSeeder @Inject constructor(
    private val nuxmvVariableTransformer: NuxmvVariableTransformer,
) {

    fun seedInitBranch(
        allVariables: List<VariableDeclaration>,
        declaredInitial: Map<VariableDeclaration, String>,
    ): NuxmvBranch {
        val constraints = mutableListOf<String>()
        val currentPrime = mutableMapOf<VariableDeclaration, String>()
        val newPrimes = mutableListOf<NuxmvPrimedDeclaration>()
        for (variable in allVariables) {
            val base = nuxmvVariableTransformer.nameOf(variable)
            val name = "${base}__${NuxmvTransitionKind.Init.tagName}_seed_0"
            val primed = NuxmvPrimedDeclaration(name, variable)
            newPrimes += primed
            currentPrime[variable] = name
            declaredInitial[variable]?.let {
                constraints += "$name = $it"
            }
        }
        return NuxmvBranch(constraints, currentPrime, newPrimes)
    }
}
