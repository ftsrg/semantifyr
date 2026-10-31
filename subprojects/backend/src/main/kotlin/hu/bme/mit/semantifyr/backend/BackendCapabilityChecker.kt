/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backend

import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import org.eclipse.emf.ecore.EObject

class UnsupportedConstruct(
    val element: EObject,
    val description: String,
)

abstract class BackendCapabilityChecker {

    fun check(inlinedOxsts: InlinedOxsts): List<UnsupportedConstruct> {
        return inlinedOxsts.eAllContents().asSequence().mapNotNull { element ->
            describeUnsupported(element)?.let {
                UnsupportedConstruct(element, it)
            }
        }.toList()
    }

    protected abstract fun describeUnsupported(element: EObject): String?

    object Unlimited : BackendCapabilityChecker() {
        override fun describeUnsupported(element: EObject): String? {
            return null
        }
    }
}
