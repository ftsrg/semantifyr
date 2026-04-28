/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.spin.transformation

import com.google.inject.Inject
import hu.bme.mit.semantifyr.backend.scopes.VerificationScoped
import hu.bme.mit.semantifyr.oxsts.lang.library.builtin.BuiltinSymbolResolver
import hu.bme.mit.semantifyr.oxsts.model.oxsts.EnumDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.EnumLiteral
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LocalVarDeclarationOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration

enum class SpinVariableKind {
    Integer,
    Boolean,
    Enum,
}

data class SpinVariable(
    val declaration: VariableDeclaration,
    val name: String,
    val kind: SpinVariableKind,
    val enumDeclaration: EnumDeclaration?,
    val initialValue: String?,
)

@VerificationScoped
class SpinVariableTransformer {
    @Inject
    private lateinit var builtinSymbolResolver: BuiltinSymbolResolver

    @Inject
    private lateinit var spinExpressionTransformer: SpinExpressionTransformer

    private val nameMap = mutableMapOf<VariableDeclaration, String>()

    fun nameOf(variableDeclaration: VariableDeclaration): String {
        return nameMap.getOrPut(variableDeclaration) {
            val base = sanitize(variableDeclaration.name)
            if (variableDeclaration is LocalVarDeclarationOperation) {
                // Promela has lexical scoping within blocks, but to avoid name clashes across branches
                // we mangle locals with a stable identity hash — same pattern as the Uppaal/nuXmv backends.
                "${base}_${(System.identityHashCode(variableDeclaration) and Int.MAX_VALUE)}"
            } else {
                base
            }
        }
    }

    fun describe(variableDeclaration: VariableDeclaration): SpinVariable {
        val name = nameOf(variableDeclaration)
        val kind = resolveKind(variableDeclaration)
        val enumDecl = (variableDeclaration.typeSpecification?.domain as? EnumDeclaration)
        val initExpr = if (variableDeclaration is LocalVarDeclarationOperation) {
            null // locals are declared inline where they appear; don't emit a global init
        } else {
            variableDeclaration.expression?.let { spinExpressionTransformer.transform(it) }
        }
        return SpinVariable(
            declaration = variableDeclaration,
            name = name,
            kind = kind,
            enumDeclaration = enumDecl,
            initialValue = initExpr,
        )
    }

    private fun resolveKind(variableDeclaration: VariableDeclaration): SpinVariableKind {
        val domain = variableDeclaration.typeSpecification?.domain
            ?: error("Variable ${variableDeclaration.name} has no type specification")

        return when {
            domain is EnumDeclaration -> SpinVariableKind.Enum
            domain === builtinSymbolResolver.intDatatype(variableDeclaration) -> SpinVariableKind.Integer
            domain === builtinSymbolResolver.boolDatatype(variableDeclaration) -> SpinVariableKind.Boolean
            else -> error("Unsupported variable domain for Spin: ${domain.name} on ${variableDeclaration.name}")
        }
    }

    fun sanitizeEnumLiteral(literal: EnumLiteral): String = sanitize(literal.name)

    private fun sanitize(name: String?): String {
        val base = name ?: "var"
        return base.replace(Regex("[^A-Za-z0-9_]"), "_")
    }
}
