/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.theta.transformation.xsts

import com.google.inject.Inject
import hu.bme.mit.semantifyr.backend.BackendCapabilityChecker
import hu.bme.mit.semantifyr.backend.BackendLimitation
import hu.bme.mit.semantifyr.oxsts.lang.library.builtin.BuiltinSymbolResolver
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ArrayLiteral
import hu.bme.mit.semantifyr.oxsts.model.oxsts.DataTypeDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ForOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralReal
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralString
import hu.bme.mit.semantifyr.oxsts.model.oxsts.RangeExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration
import org.eclipse.emf.ecore.EObject

class ThetaCapabilityChecker @Inject constructor(
    private val builtinSymbolResolver: BuiltinSymbolResolver,
) : BackendCapabilityChecker() {

    override fun describeUnsupported(element: EObject): String? {
        return when (element) {
            is VariableDeclaration -> describeUnsupportedType(element)
            is ForOperation -> describeUnsupportedRange(element)
            is LiteralReal -> "real literals"
            is LiteralString -> "string literals"
            is ArrayLiteral -> "array literals"
            else -> null
        }
    }

    private fun describeUnsupportedRange(operation: ForOperation): String? {
        if (operation.rangeExpression is RangeExpression) {
            return null
        }
        return "for-operations over anything but a range"
    }

    private fun describeUnsupportedType(variable: VariableDeclaration): String? {
        val domain = variable.typeSpecification?.domain as? DataTypeDeclaration ?: return null
        if (domain === builtinSymbolResolver.intDatatype(variable) || domain === builtinSymbolResolver.boolDatatype(variable)) {
            return null
        }
        return "variables of type ${domain.name}"
    }

    companion object {
        val limitations = listOf(
            BackendLimitation("variables of type real", "XSTS has integer, boolean and enumeration variables only"),
            BackendLimitation("variables of type string", "XSTS has integer, boolean and enumeration variables only"),
            BackendLimitation("real literals", "XSTS has no real numbers"),
            BackendLimitation("string literals", "XSTS has no strings"),
            BackendLimitation("array literals", "the backend receives arrays split into one variable per slot and has no form for an array value"),
            BackendLimitation("for-operations over anything but a range", "an XSTS loop runs over an integer range"),
        )
    }
}
