/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.nuxmv.transformation

import com.google.inject.Inject
import hu.bme.mit.semantifyr.oxsts.lang.library.builtin.BuiltinSymbolResolver
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration
import org.eclipse.emf.ecore.EObject

// the translation declares every integer unbounded, and the BDD engine builds no FSM over infinite precision variables
class NuxmvBddCapabilityChecker @Inject constructor(
    builtinSymbolResolver: BuiltinSymbolResolver,
) : NuxmvCapabilityChecker(builtinSymbolResolver) {

    override fun describeUnsupported(element: EObject): String? {
        if (element is VariableDeclaration && element.typeSpecification?.domain === builtinSymbolResolver.intDatatype(element)) {
            return "integer variables"
        }
        return super.describeUnsupported(element)
    }
}
