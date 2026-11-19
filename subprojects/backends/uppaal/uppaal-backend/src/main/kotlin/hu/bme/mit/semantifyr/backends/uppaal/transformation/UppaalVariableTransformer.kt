/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.uppaal.transformation

import com.google.inject.Inject
import hu.bme.mit.semantifyr.backend.BackendUnsupportedException
import hu.bme.mit.semantifyr.backend.scopes.VerificationScoped
import hu.bme.mit.semantifyr.backend.transformation.BackendNameMangler
import hu.bme.mit.semantifyr.backend.witness.BackendVariableNames
import hu.bme.mit.semantifyr.oxsts.lang.library.builtin.BuiltinSymbolResolver
import hu.bme.mit.semantifyr.oxsts.model.oxsts.EnumDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.EnumLiteral
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration

enum class UppaalVariableKind {
    Clock,
    Integer,
    Boolean,
    Enum,
}

data class UppaalVariable(
    val name: String,
    val kind: UppaalVariableKind,
    val enumDeclaration: EnumDeclaration?,
)

@VerificationScoped
class UppaalVariableTransformer @Inject constructor(
    private val builtinSymbolResolver: BuiltinSymbolResolver,
) : BackendVariableNames {

    private val mangler = BackendNameMangler()
    private val descriptionCache = mutableMapOf<VariableDeclaration, UppaalVariable>()

    override fun nameOf(variableDeclaration: VariableDeclaration): String {
        return mangler.nameOf(variableDeclaration)
    }

    fun variableOf(name: String): VariableDeclaration? {
        return mangler.variableOf(name)
    }

    fun describe(variableDeclaration: VariableDeclaration): UppaalVariable {
        return descriptionCache.getOrPut(variableDeclaration) {
            val name = nameOf(variableDeclaration)
            val kind = resolveKind(variableDeclaration)
            val enumDeclaration = variableDeclaration.typeSpecification?.domain as? EnumDeclaration
            UppaalVariable(name, kind, enumDeclaration)
        }
    }

    fun kindOf(variableDeclaration: VariableDeclaration): UppaalVariableKind? {
        val domain = variableDeclaration.typeSpecification?.domain
        return when {
            domain === builtinSymbolResolver.clockDatatype(variableDeclaration) -> UppaalVariableKind.Clock
            domain is EnumDeclaration -> UppaalVariableKind.Enum
            domain === builtinSymbolResolver.intDatatype(variableDeclaration) -> UppaalVariableKind.Integer
            domain === builtinSymbolResolver.boolDatatype(variableDeclaration) -> UppaalVariableKind.Boolean
            else -> null
        }
    }

    private fun resolveKind(variableDeclaration: VariableDeclaration): UppaalVariableKind {
        val domain = variableDeclaration.typeSpecification?.domain
            ?: error("Variable ${variableDeclaration.name} has no type specification")
        return kindOf(variableDeclaration) ?: throw BackendUnsupportedException("Uppaal does not support variables of type ${domain.name} (${variableDeclaration.name})")
    }

    fun nameOf(enumDeclaration: EnumDeclaration): String {
        return mangler.nameOf(enumDeclaration)
    }

    fun nameOf(enumLiteral: EnumLiteral): String {
        return mangler.nameOf(enumLiteral)
    }
}
