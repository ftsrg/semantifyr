/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.nuxmv.transformation

import com.google.inject.Inject
import hu.bme.mit.semantifyr.backend.BackendCapabilityChecker
import hu.bme.mit.semantifyr.backend.BackendLimitation
import hu.bme.mit.semantifyr.oxsts.lang.library.builtin.BuiltinSymbolResolver
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ArrayLiteral
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ForOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.IndexingSuffixExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralString
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration
import org.eclipse.emf.ecore.EObject

class NuxmvCapabilityChecker @Inject constructor(
    private val builtinSymbolResolver: BuiltinSymbolResolver,
) : BackendCapabilityChecker() {

    override fun describeUnsupported(element: EObject): Limitation? {
        return when (element) {
            is VariableDeclaration -> describeUnsupportedType(element)
            is ForOperation -> Limitation.ForOperations
            is LiteralString -> Limitation.StringLiterals
            is ArrayLiteral -> Limitation.ArrayLiterals
            is IndexingSuffixExpression -> Limitation.ArrayIndexing
            else -> null
        }
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
        RealVariables("variables of type real", "the SMV translation declares integer, boolean and enumeration variables only"),
        StringVariables("variables of type string", "the SMV translation declares integer, boolean and enumeration variables only"),
        ForOperations("for-operations", "the SMV translation has no loop"),
        StringLiterals("string literals", "SMV has no strings"),
        ArrayLiterals("array literals", "the backend receives arrays split into one variable per slot and has no form for an array value"),
        ArrayIndexing("array indexing", "the backend receives arrays split into one variable per slot and has no form for indexing"),
    }
}
