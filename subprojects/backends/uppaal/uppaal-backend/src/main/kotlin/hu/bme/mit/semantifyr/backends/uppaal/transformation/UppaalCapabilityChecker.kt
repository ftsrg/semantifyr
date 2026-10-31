/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.uppaal.transformation

import com.google.inject.Inject
import hu.bme.mit.semantifyr.backend.BackendCapabilityChecker
import hu.bme.mit.semantifyr.oxsts.lang.library.builtin.BuiltinAnnotationHandler
import hu.bme.mit.semantifyr.oxsts.lang.library.builtin.BuiltinSymbolResolver
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ArrayLiteral
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AssignmentOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.DataTypeDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ElementReference
import hu.bme.mit.semantifyr.oxsts.model.oxsts.EnumDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ForOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.HavocOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.IndexingSuffixExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralInteger
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralString
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration
import org.eclipse.emf.ecore.EObject

class UppaalCapabilityChecker @Inject constructor(
    private val builtinSymbolResolver: BuiltinSymbolResolver,
    private val builtinAnnotationHandler: BuiltinAnnotationHandler,
) : BackendCapabilityChecker() {

    override fun describeUnsupported(element: EObject): String? {
        return when (element) {
            is VariableDeclaration -> describeUnsupportedType(element)
            is AssignmentOperation -> describeUnsupportedClockReset(element)
            is HavocOperation -> describeUnsupportedClockHavoc(element)
            is ForOperation -> "for-operations"
            is LiteralString -> "string literals"
            is ArrayLiteral -> "array literals"
            is IndexingSuffixExpression -> "array indexing"
            else -> null
        }
    }

    private fun describeUnsupportedType(variable: VariableDeclaration): String? {
        val domain = variable.typeSpecification?.domain ?: return null
        if (builtinAnnotationHandler.isClockVariable(variable)) {
            if (domain is DataTypeDeclaration) {
                return null
            }
            return "@Clock on variables that are not data-typed"
        }
        if (domain is EnumDeclaration || domain === builtinSymbolResolver.intDatatype(variable) || domain === builtinSymbolResolver.boolDatatype(variable)) {
            return null
        }
        return "variables of type ${domain.name}"
    }

    private fun describeUnsupportedClockReset(operation: AssignmentOperation): String? {
        if (!isClockReference(operation.reference)) {
            return null
        }
        if ((operation.expression as? LiteralInteger)?.value == 0) {
            return null
        }
        return "clock resets to values other than 0"
    }

    private fun describeUnsupportedClockHavoc(operation: HavocOperation): String? {
        if (!isClockReference(operation.reference)) {
            return null
        }
        return "havoc on clocks"
    }

    private fun isClockReference(reference: Expression): Boolean {
        val variable = (reference as? ElementReference)?.element as? VariableDeclaration ?: return false
        return builtinAnnotationHandler.isClockVariable(variable)
    }
}
