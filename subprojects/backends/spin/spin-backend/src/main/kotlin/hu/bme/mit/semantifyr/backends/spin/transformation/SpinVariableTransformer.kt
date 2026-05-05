/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.spin.transformation

import com.google.inject.Inject
import hu.bme.mit.semantifyr.backend.scopes.VerificationScoped
import hu.bme.mit.semantifyr.backend.transformation.BackendNameMangler
import hu.bme.mit.semantifyr.oxsts.lang.library.builtin.BuiltinSymbolResolver
import hu.bme.mit.semantifyr.oxsts.model.oxsts.EnumDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.EnumLiteral
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration

enum class SpinVariableKind {
    Integer,
    Boolean,
    Enum,
}

data class SpinVariable(
    val name: String,
    val kind: SpinVariableKind,
    val enumDeclaration: EnumDeclaration?,
)

@VerificationScoped
class SpinVariableTransformer @Inject constructor(
    private val builtinSymbolResolver: BuiltinSymbolResolver,
) {

    private val mangler = BackendNameMangler()
    private val descriptionCache = mutableMapOf<VariableDeclaration, SpinVariable>()

    fun nameOf(variableDeclaration: VariableDeclaration): String {
        return mangler.nameOf(variableDeclaration)
    }

    fun describe(variableDeclaration: VariableDeclaration): SpinVariable {
        return descriptionCache.getOrPut(variableDeclaration) {
            val name = nameOf(variableDeclaration)
            val kind = resolveKind(variableDeclaration)
            val enumDeclaration = variableDeclaration.typeSpecification?.domain as? EnumDeclaration
            SpinVariable(name, kind, enumDeclaration)
        }
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

    fun sanitizeEnumLiteral(literal: EnumLiteral): String {
        return mangler.sanitize(literal.name)
    }
}
