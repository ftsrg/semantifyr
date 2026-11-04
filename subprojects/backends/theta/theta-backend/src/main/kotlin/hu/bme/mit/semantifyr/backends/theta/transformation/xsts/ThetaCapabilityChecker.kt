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
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ForOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralReal
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralString
import hu.bme.mit.semantifyr.oxsts.model.oxsts.RangeExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration
import org.eclipse.emf.ecore.EObject

class ThetaCapabilityChecker @Inject constructor(
    private val builtinSymbolResolver: BuiltinSymbolResolver,
) : BackendCapabilityChecker() {

    override fun describeUnsupported(element: EObject): Limitation? {
        return when (element) {
            is VariableDeclaration -> describeUnsupportedType(element)
            is ForOperation -> describeUnsupportedRange(element)
            is LiteralReal -> Limitation.RealLiterals
            is LiteralString -> Limitation.StringLiterals
            is ArrayLiteral -> Limitation.ArrayLiterals
            else -> null
        }
    }

    private fun describeUnsupportedRange(operation: ForOperation): Limitation? {
        if (operation.rangeExpression is RangeExpression) {
            return null
        }
        return Limitation.NonRangeForOperations
    }

    private fun describeUnsupportedType(variable: VariableDeclaration): Limitation? {
        return when (variable.typeSpecification?.domain) {
            builtinSymbolResolver.realDatatype(variable) -> Limitation.RealVariables
            builtinSymbolResolver.stringDatatype(variable) -> Limitation.StringVariables
            else -> null
        }
    }

    enum class Limitation(
        override val construct: String,
        override val reason: String,
    ) : BackendLimitation {
        RealVariables("variables of type real", "XSTS has integer, boolean and enumeration variables only"),
        StringVariables("variables of type string", "XSTS has integer, boolean and enumeration variables only"),
        RealLiterals("real literals", "XSTS has no real numbers"),
        StringLiterals("string literals", "XSTS has no strings"),
        ArrayLiterals("array literals", "the backend receives arrays split into one variable per slot and has no form for an array value"),
        NonRangeForOperations("for-operations over anything but a range", "an XSTS loop runs over an integer range"),
    }
}
