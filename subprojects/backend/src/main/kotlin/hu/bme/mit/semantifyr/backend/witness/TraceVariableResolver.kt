/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backend.witness

import hu.bme.mit.semantifyr.compiler.pipeline.utils.eAllOfType
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration

interface BackendVariableNames {
    fun nameOf(variableDeclaration: VariableDeclaration): String
}

class TraceVariableResolver(
    inlinedOxsts: InlinedOxsts,
    names: BackendVariableNames,
    private val addedNames: Set<String>,
    private val trace: String,
) {

    // a trace names state variables, so a state variable wins a name it shares with a local
    private val variablesByName = inlinedOxsts.eAllOfType<VariableDeclaration>().associateBy {
        names.nameOf(it)
    } + inlinedOxsts.variables.associateBy {
        names.nameOf(it)
    }

    private val stateVariables = inlinedOxsts.variables.toSet()

    fun stateVariableOf(name: String): VariableDeclaration? {
        if (name in addedNames) {
            return null
        }
        val variable = variablesByName[name] ?: throw WitnessFailedException("$trace holds $name, which names no variable of the model")
        if (variable !in stateVariables) {
            return null
        }
        return variable
    }
}
