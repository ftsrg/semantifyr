/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.uppaal.transformation

import com.google.inject.Inject
import hu.bme.mit.semantifyr.backends.uppaal.ir.UppaalDeclarations
import hu.bme.mit.semantifyr.backends.uppaal.ir.UppaalVariableDecl
import hu.bme.mit.semantifyr.oxsts.model.oxsts.EnumDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LocalVarDeclarationOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration

class UppaalDeclarationsBuilder @Inject constructor(
    private val uppaalVariableTransformer: UppaalVariableTransformer,
    private val uppaalExpressionTransformer: UppaalExpressionTransformer,
) {

    fun build(
        variables: List<VariableDeclaration>,
        enums: List<EnumDeclaration>,
    ): UppaalDeclarations {
        val typedefs = buildEnumLiteralConstants(enums)
        val variableDeclarations = mutableListOf<UppaalVariableDecl>()
        for (variable in variables) {
            variableDeclarations += renderVariable(variable)
        }
        return UppaalDeclarations(typedefs, variableDeclarations)
    }

    private fun renderVariable(variable: VariableDeclaration): UppaalVariableDecl {
        val described = uppaalVariableTransformer.describe(variable)
        val typeName = renderTypeName(described.kind)
        val initialValue = if (described.kind == UppaalVariableKind.Clock) {
            null
        } else {
            renderInitialValue(variable)
        }
        return UppaalVariableDecl(typeName, described.name, initialValue)
    }

    private fun renderTypeName(kind: UppaalVariableKind): String {
        return when (kind) {
            UppaalVariableKind.Clock -> "clock"
            UppaalVariableKind.Integer -> "int"
            UppaalVariableKind.Boolean -> "bool"
            // Enum literals are emitted as const-int aliases above; the var itself stores an int.
            UppaalVariableKind.Enum -> "int"
        }
    }

    private fun renderInitialValue(variable: VariableDeclaration): String? {
        if (variable is LocalVarDeclarationOperation) {
            return null
        }
        val expression = variable.expression ?: return null
        return uppaalExpressionTransformer.transform(expression)
    }

    private fun buildEnumLiteralConstants(enums: List<EnumDeclaration>): List<String> {
        val lines = mutableListOf<String>()
        for (enum in enums) {
            lines += "// enum ${enum.name}"
            enum.literals.forEachIndexed { index, literal ->
                val name = uppaalVariableTransformer.sanitizeEnumLiteral(literal)
                lines += "const int $name = $index;"
            }
        }
        return lines
    }
}
