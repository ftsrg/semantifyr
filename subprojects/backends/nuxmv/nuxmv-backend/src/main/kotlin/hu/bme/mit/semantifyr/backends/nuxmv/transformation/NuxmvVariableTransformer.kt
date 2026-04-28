/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.nuxmv.transformation

import com.google.inject.Inject
import hu.bme.mit.semantifyr.backend.scopes.VerificationScoped
import hu.bme.mit.semantifyr.oxsts.lang.library.builtin.BuiltinSymbolResolver
import hu.bme.mit.semantifyr.oxsts.model.oxsts.EnumDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.EnumLiteral
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LocalVarDeclarationOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration

enum class NuxmvVariableKind {
    Integer,
    Boolean,
    Enum,
}

data class NuxmvVariable(
    val declaration: VariableDeclaration,
    val name: String,
    val kind: NuxmvVariableKind,
    val enumDeclaration: EnumDeclaration?,
)

@VerificationScoped
class NuxmvVariableTransformer {
    @Inject
    private lateinit var builtinSymbolResolver: BuiltinSymbolResolver

    private val nameMap = mutableMapOf<VariableDeclaration, String>()

    fun nameOf(variableDeclaration: VariableDeclaration): String {
        return nameMap.getOrPut(variableDeclaration) {
            val base = sanitize(variableDeclaration.name)
            if (variableDeclaration is LocalVarDeclarationOperation) {
                // Locals are hoisted to the flat SMV global namespace; mangle them so each decl site is unique
                "${base}_${(System.identityHashCode(variableDeclaration) and Int.MAX_VALUE)}"
            } else {
                base
            }
        }
    }

    fun describe(variableDeclaration: VariableDeclaration): NuxmvVariable {
        val name = nameOf(variableDeclaration)
        val kind = resolveKind(variableDeclaration)
        val enumDecl = (variableDeclaration.typeSpecification?.domain as? EnumDeclaration)
        return NuxmvVariable(
            declaration = variableDeclaration,
            name = name,
            kind = kind,
            enumDeclaration = enumDecl,
        )
    }

    private fun resolveKind(variableDeclaration: VariableDeclaration): NuxmvVariableKind {
        val domain = variableDeclaration.typeSpecification?.domain
            ?: error("Variable ${variableDeclaration.name} has no type specification")

        return when {
            domain is EnumDeclaration -> NuxmvVariableKind.Enum
            domain === builtinSymbolResolver.intDatatype(variableDeclaration) -> NuxmvVariableKind.Integer
            domain === builtinSymbolResolver.boolDatatype(variableDeclaration) -> NuxmvVariableKind.Boolean
            else -> error("Unsupported variable domain for nuXmv: ${domain.name} on ${variableDeclaration.name}")
        }
    }

    fun sanitizeEnumLiteral(literal: EnumLiteral): String = sanitize(literal.name)

    private fun sanitize(name: String?): String {
        val base = name ?: "var"
        return base.replace(Regex("[^A-Za-z0-9_]"), "_")
    }
}
