/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backend

import hu.bme.mit.semantifyr.oxsts.model.oxsts.Annotation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import org.eclipse.emf.ecore.EObject

class UnsupportedConstruct(
    val element: EObject,
    val description: String,
)

abstract class BackendCapabilityChecker {

    fun check(inlinedOxsts: InlinedOxsts): List<UnsupportedConstruct> {
        val constructs = mutableListOf<UnsupportedConstruct>()
        val contents = inlinedOxsts.eAllContents()
        for (element in contents) {
            // no annotation argument reaches an engine model
            if (element is Annotation) {
                contents.prune()
                continue
            }
            describeUnsupported(element)?.let {
                constructs += UnsupportedConstruct(element, it)
            }
        }
        return constructs + checkModel(inlinedOxsts)
    }

    protected abstract fun describeUnsupported(element: EObject): String?

    protected open fun checkModel(inlinedOxsts: InlinedOxsts): List<UnsupportedConstruct> {
        return emptyList()
    }

    object Unlimited : BackendCapabilityChecker() {
        override fun describeUnsupported(element: EObject): String? {
            return null
        }
    }
}
