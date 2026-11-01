/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.uppaal.transformation

import com.google.inject.Inject
import hu.bme.mit.semantifyr.backend.transformation.IntegerRangeChecker
import hu.bme.mit.semantifyr.backends.uppaal.ir.UppaalDeclarations
import hu.bme.mit.semantifyr.backends.uppaal.ir.UppaalVariableDecl
import hu.bme.mit.semantifyr.oxsts.model.oxsts.EnumDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LocalVarDeclarationOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration

class UppaalDeclarationsBuilder @Inject constructor(
    private val uppaalVariableTransformer: UppaalVariableTransformer,
    private val uppaalExpressionTransformer: UppaalExpressionTransformer,
    private val integerRangeChecker: IntegerRangeChecker,
) {

    fun build(
        inlinedOxsts: InlinedOxsts,
        variables: List<VariableDeclaration>,
        enums: List<EnumDeclaration>,
    ): UppaalDeclarations {
        val typedefs = buildEnumLiteralConstants(enums)
        val variableDeclarations = mutableListOf<UppaalVariableDecl>()
        for (variable in variables) {
            variableDeclarations += renderVariable(inlinedOxsts, variable)
        }
        return UppaalDeclarations(typedefs, variableDeclarations)
    }

    private fun renderVariable(inlinedOxsts: InlinedOxsts, variable: VariableDeclaration): UppaalVariableDecl {
        val described = uppaalVariableTransformer.describe(variable)
        val typeName = renderTypeName(inlinedOxsts, variable, described.kind)
        val initialValue = if (described.kind == UppaalVariableKind.Clock) {
            null
        } else {
            renderInitialValue(variable)
        }
        return UppaalVariableDecl(typeName, described.name, initialValue)
    }

    private fun renderTypeName(
        inlinedOxsts: InlinedOxsts,
        variable: VariableDeclaration,
        kind: UppaalVariableKind,
    ): String {
        return when (kind) {
            UppaalVariableKind.Clock -> "clock"
            // a declared range lifts the default 16-bit bound, and Uppaal reports an assignment outside it instead of a verdict
            UppaalVariableKind.Integer -> integerRangeChecker.declaredRangeOf(variable, inlinedOxsts).let {
                "int[${it.lower},${it.upper}]"
            }
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
