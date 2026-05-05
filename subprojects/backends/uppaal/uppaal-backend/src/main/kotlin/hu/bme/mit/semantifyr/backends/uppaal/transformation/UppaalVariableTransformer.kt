/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.uppaal.transformation

import com.google.inject.Inject
import hu.bme.mit.semantifyr.backend.scopes.VerificationScoped
import hu.bme.mit.semantifyr.backend.transformation.BackendNameMangler
import hu.bme.mit.semantifyr.oxsts.lang.library.builtin.BuiltinAnnotationHandler
import hu.bme.mit.semantifyr.oxsts.lang.library.builtin.BuiltinSymbolResolver
import hu.bme.mit.semantifyr.oxsts.model.oxsts.DataTypeDeclaration
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
    private val builtinAnnotationHandler: BuiltinAnnotationHandler,
    private val builtinSymbolResolver: BuiltinSymbolResolver,
) {

    private val mangler = BackendNameMangler()
    private val descriptionCache = mutableMapOf<VariableDeclaration, UppaalVariable>()

    fun nameOf(variableDeclaration: VariableDeclaration): String {
        return mangler.nameOf(variableDeclaration)
    }

    fun isClock(variableDeclaration: VariableDeclaration): Boolean {
        return builtinAnnotationHandler.isClockVariable(variableDeclaration)
    }

    fun describe(variableDeclaration: VariableDeclaration): UppaalVariable {
        return descriptionCache.getOrPut(variableDeclaration) {
            val name = nameOf(variableDeclaration)
            val kind = resolveKind(variableDeclaration)
            val enumDeclaration = variableDeclaration.typeSpecification?.domain as? EnumDeclaration
            UppaalVariable(name, kind, enumDeclaration)
        }
    }

    private fun resolveKind(variableDeclaration: VariableDeclaration): UppaalVariableKind {
        if (isClock(variableDeclaration)) {
            return resolveClockKind(variableDeclaration)
        }
        val domain = variableDeclaration.typeSpecification?.domain
            ?: error("Variable ${variableDeclaration.name} has no type specification")

        return when {
            domain is EnumDeclaration -> UppaalVariableKind.Enum
            domain === builtinSymbolResolver.intDatatype(variableDeclaration) -> UppaalVariableKind.Integer
            domain === builtinSymbolResolver.boolDatatype(variableDeclaration) -> UppaalVariableKind.Boolean
            else -> error("Unsupported variable domain for Uppaal: ${domain.name} on ${variableDeclaration.name}")
        }
    }

    private fun resolveClockKind(variableDeclaration: VariableDeclaration): UppaalVariableKind {
        val domain = variableDeclaration.typeSpecification?.domain
        if (domain !is DataTypeDeclaration) {
            error("@Clock annotations are only supported on data-typed variables; got ${domain?.javaClass?.simpleName} on ${variableDeclaration.name}")
        }
        return UppaalVariableKind.Clock
    }

    fun sanitizeEnumLiteral(literal: EnumLiteral): String {
        return mangler.sanitize(literal.name)
    }
}
