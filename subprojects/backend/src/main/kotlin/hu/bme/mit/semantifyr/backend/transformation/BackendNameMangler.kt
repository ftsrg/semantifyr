/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backend.transformation

import hu.bme.mit.semantifyr.oxsts.model.oxsts.EnumDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.EnumLiteral
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LocalVarDeclarationOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.OxstsModelPackage
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration
import org.eclipse.emf.ecore.EObject

private const val INSTANCE_SEPARATOR = "$$"

fun qualifiedEnumName(enumDeclaration: EnumDeclaration): String {
    val packageName = (enumDeclaration.eContainer() as? OxstsModelPackage)?.name
    return listOfNotNull(packageName, enumDeclaration.name).joinToString("::").replace("::", INSTANCE_SEPARATOR)
}

// injective: every escape and marker starts with '_' and none is a prefix of another
class BackendNameMangler {

    private val nameMap = mutableMapOf<EObject, String>()
    private var localCount = 0

    fun nameOf(variable: VariableDeclaration): String {
        return nameMap.getOrPut(variable) {
            if (variable is LocalVarDeclarationOperation) {
                "${encode(variable.name)}_l${localCount++}"
            } else {
                encode(variable.name)
            }
        }
    }

    fun nameOf(enumDeclaration: EnumDeclaration): String {
        return nameMap.getOrPut(enumDeclaration) {
            "${encode(qualifiedEnumName(enumDeclaration))}_t"
        }
    }

    fun nameOf(enumLiteral: EnumLiteral): String {
        return nameMap.getOrPut(enumLiteral) {
            val enumDeclaration = enumLiteral.eContainer() as EnumDeclaration
            "${encode(qualifiedEnumName(enumDeclaration))}_e${encode(enumLiteral.name)}"
        }
    }

    fun variableOf(name: String): VariableDeclaration? {
        return nameMap.entries.firstOrNull {
            it.key is VariableDeclaration && it.value == name
        }?.key as VariableDeclaration?
    }

    private fun encode(name: String): String {
        val builder = StringBuilder()
        var index = 0
        while (index < name.length) {
            val character = name[index]
            when {
                name.startsWith(INSTANCE_SEPARATOR, index) -> {
                    builder.append("__")
                    index++
                }
                character == '$' -> {
                    builder.append("_d")
                }
                character == '_' -> {
                    builder.append("_u")
                }
                character in 'a'..'z' || character in 'A'..'Z' || character in '0'..'9' -> {
                    builder.append(character)
                }
                else -> {
                    builder.append("_x${character.code.toString(16)}_")
                }
            }
            index++
        }
        return builder.toString()
    }
}
