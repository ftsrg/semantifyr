/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.spin.transformation

import com.google.inject.Inject
import hu.bme.mit.semantifyr.oxsts.model.oxsts.EnumDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration
import hu.bme.mit.semantifyr.utils.text.IndentingStringBuilder

class SpinDeclarationRenderer @Inject constructor(
    private val spinVariableTransformer: SpinVariableTransformer,
    private val spinExpressionTransformer: SpinExpressionTransformer,
) {

    fun renderEnums(builder: IndentingStringBuilder, enums: List<EnumDeclaration>) {
        if (enums.isEmpty()) {
            return
        }
        for (enum in enums) {
            val literals = enum.literals.joinToString(", ") {
                spinVariableTransformer.sanitizeEnumLiteral(it)
            }
            builder.appendLine("mtype:${enum.name} = { $literals };")
        }
        builder.appendLine()
    }

    fun renderGlobals(
        builder: IndentingStringBuilder,
        globals: List<VariableDeclaration>,
    ) {
        if (globals.isEmpty()) {
            return
        }
        for (variable in globals) {
            builder.appendLine(renderGlobal(variable))
        }
        builder.appendLine()
    }

    private fun renderGlobal(variable: VariableDeclaration): String {
        val described = spinVariableTransformer.describe(variable)
        val type = renderGlobalType(described)
        val initializer = variable.expression?.let {
            " = ${spinExpressionTransformer.transform(it)}"
        } ?: ""
        return "$type ${described.name}$initializer;"
    }

    private fun renderGlobalType(variable: SpinVariable): String {
        return when (variable.kind) {
            SpinVariableKind.Integer -> "int"
            SpinVariableKind.Boolean -> "bool"
            SpinVariableKind.Enum -> "mtype:${variable.enumDeclaration!!.name}"
        }
    }

    fun renderStableFlag(builder: IndentingStringBuilder) {
        builder.appendLine("bool $SPIN_STABLE_FLAG = false;")
        builder.appendLine()
    }
}
