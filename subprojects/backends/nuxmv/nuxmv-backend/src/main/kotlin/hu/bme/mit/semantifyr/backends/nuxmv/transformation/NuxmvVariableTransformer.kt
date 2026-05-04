/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.nuxmv.transformation

import com.google.inject.Inject
import hu.bme.mit.semantifyr.backend.scopes.VerificationScoped
import hu.bme.mit.semantifyr.backend.transformation.BackendNameMangler
import hu.bme.mit.semantifyr.oxsts.lang.library.builtin.BuiltinSymbolResolver
import hu.bme.mit.semantifyr.oxsts.model.oxsts.EnumDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.EnumLiteral
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

    private val mangler = BackendNameMangler()

    fun nameOf(variableDeclaration: VariableDeclaration): String {
        return mangler.nameOf(variableDeclaration)
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

    fun sanitizeEnumLiteral(literal: EnumLiteral): String {
        return mangler.sanitize(literal.name)
    }
}
