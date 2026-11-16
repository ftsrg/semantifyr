/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.nuxmv.transformation

import com.google.inject.Inject
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration
import org.eclipse.emf.ecore.EObject

class NuxmvBddCapabilityChecker @Inject constructor(
    nuxmvVariableTransformer: NuxmvVariableTransformer,
) : NuxmvCapabilityChecker(nuxmvVariableTransformer) {

    override fun describeUnsupported(element: EObject): String? {
        if (element !is VariableDeclaration) {
            return super.describeUnsupported(element)
        }
        return when (nuxmvVariableTransformer.kindOf(element)) {
            NuxmvVariableKind.Integer -> "integer variables"
            NuxmvVariableKind.Clock -> "clocks"
            else -> super.describeUnsupported(element)
        }
    }
}
