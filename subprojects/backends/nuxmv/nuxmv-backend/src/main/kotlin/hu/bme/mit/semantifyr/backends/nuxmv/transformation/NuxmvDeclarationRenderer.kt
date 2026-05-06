/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.nuxmv.transformation

import com.google.inject.Inject
import hu.bme.mit.semantifyr.utils.text.IndentingStringBuilder

class NuxmvDeclarationRenderer @Inject constructor(
    private val nuxmvVariableTransformer: NuxmvVariableTransformer,
) {

    fun renderVariablesSection(
        builder: IndentingStringBuilder,
        variables: List<NuxmvVariable>,
        primedDeclarations: List<NuxmvPrimedDeclaration>,
    ) {
        builder.appendLine("VAR")
        builder.indented {
            for (variable in variables) {
                appendLine("${variable.name} : ${renderType(variable)};")
            }
            for (primed in primedDeclarations) {
                val variable = nuxmvVariableTransformer.describe(primed.variable)
                appendLine("${primed.name} : ${renderType(variable)};")
            }
        }
    }

    fun renderInputVariablesSection(builder: IndentingStringBuilder, inputVariables: List<NuxmvIVariable>) {
        if (inputVariables.isEmpty()) {
            return
        }
        builder.appendLine("IVAR")
        builder.indented {
            for (ivar in inputVariables) {
                appendLine("${ivar.name} : ${ivar.smvType};")
            }
        }
    }

    fun renderFrozenVariablesSection(builder: IndentingStringBuilder, frozenVariables: List<NuxmvFrozenVariable>) {
        if (frozenVariables.isEmpty()) {
            return
        }
        builder.appendLine("FROZENVAR")
        builder.indented {
            for (frozen in frozenVariables) {
                appendLine("${frozen.name} : ${frozen.smvType};")
            }
        }
    }

    private fun renderType(variable: NuxmvVariable): String {
        return when (variable.kind) {
            NuxmvVariableKind.Integer -> "integer"
            NuxmvVariableKind.Boolean -> "boolean"
            NuxmvVariableKind.Enum -> renderEnumType(variable)
        }
    }

    private fun renderEnumType(variable: NuxmvVariable): String {
        val enum = variable.enumDeclaration ?: error("Enum variable ${variable.name} has no enum declaration")
        return enum.literals.joinToString(", ", "{ ", " }") {
            it.name
        }
    }
}
