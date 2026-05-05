/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.nuxmv.transformation

import com.google.inject.Inject
import hu.bme.mit.semantifyr.backend.text.IndentingBuilder

class NuxmvDeclarationRenderer @Inject constructor(
    private val nuxmvVariableTransformer: NuxmvVariableTransformer,
) {

    fun renderVariablesSection(
        builder: IndentingBuilder,
        variables: List<NuxmvVariable>,
        primedDeclarations: List<NuxmvPrimedDeclaration>,
    ) {
        builder.line("VAR")
        builder.indented {
            for (variable in variables) {
                line("${variable.name} : ${renderType(variable)};")
            }
            for (primed in primedDeclarations) {
                val variable = nuxmvVariableTransformer.describe(primed.variable)
                line("${primed.name} : ${renderType(variable)};")
            }
        }
    }

    fun renderInputVariablesSection(builder: IndentingBuilder, inputVariables: List<NuxmvIVariable>) {
        if (inputVariables.isEmpty()) {
            return
        }
        builder.line("IVAR")
        builder.indented {
            for (ivar in inputVariables) {
                line("${ivar.name} : ${ivar.smvType};")
            }
        }
    }

    fun renderFrozenVariablesSection(builder: IndentingBuilder, frozenVariables: List<NuxmvFrozenVariable>) {
        if (frozenVariables.isEmpty()) {
            return
        }
        builder.line("FROZENVAR")
        builder.indented {
            for (frozen in frozenVariables) {
                line("${frozen.name} : ${frozen.smvType};")
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
