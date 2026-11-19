/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.nuxmv.transformation

import com.google.inject.Inject
import hu.bme.mit.semantifyr.backend.BackendCapabilityChecker
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ArrayLiteral
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ForOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.IndexingSuffixExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralString
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration
import org.eclipse.emf.ecore.EObject

open class NuxmvCapabilityChecker @Inject constructor(
    protected val nuxmvVariableTransformer: NuxmvVariableTransformer,
) : BackendCapabilityChecker() {

    override fun describeUnsupported(element: EObject): String? {
        return when (element) {
            is VariableDeclaration -> describeUnsupportedType(element)
            is ForOperation -> "for-operations"
            is LiteralString -> "string literals"
            is ArrayLiteral -> "array literals"
            is IndexingSuffixExpression -> "array indexing"
            else -> null
        }
    }

    private fun describeUnsupportedType(variable: VariableDeclaration): String? {
        val domain = variable.typeSpecification?.domain ?: return null
        if (nuxmvVariableTransformer.kindOf(variable) != null) {
            return null
        }
        return "variables of type ${domain.name}"
    }
}
