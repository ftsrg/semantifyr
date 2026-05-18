/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backend.transformation

import hu.bme.mit.semantifyr.oxsts.model.oxsts.LocalVarDeclarationOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration

private val IDENTIFIER_REGEX = Regex("[^A-Za-z0-9_]")

class BackendNameMangler {

    private val nameMap = mutableMapOf<VariableDeclaration, String>()

    fun nameOf(variable: VariableDeclaration): String {
        return nameMap.getOrPut(variable) {
            val base = sanitize(variable.name)
            if (variable is LocalVarDeclarationOperation) {
                "${base}_${System.identityHashCode(variable) and Int.MAX_VALUE}"
            } else {
                base
            }
        }
    }

    fun variableOf(name: String): VariableDeclaration? {
        return nameMap.entries.firstOrNull {
            it.value == name
        }?.key
    }

    fun sanitize(name: String): String {
        return name.replace(IDENTIFIER_REGEX, "_")
    }
}
