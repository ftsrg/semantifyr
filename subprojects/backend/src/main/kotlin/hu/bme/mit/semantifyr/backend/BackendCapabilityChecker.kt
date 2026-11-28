/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backend

import hu.bme.mit.semantifyr.compiler.pipeline.context.TransitionCallTraceMap
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Annotation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import org.eclipse.emf.ecore.EObject

class UnsupportedConstruct(
    val element: EObject,
    val description: String,
)

abstract class BackendCapabilityChecker {

    fun check(inlinedOxsts: InlinedOxsts, transitionCallTraces: TransitionCallTraceMap): List<UnsupportedConstruct> {
        val constructs = mutableListOf<UnsupportedConstruct>()
        val contents = inlinedOxsts.eAllContents()
        for (element in contents) {
            // no annotation argument reaches a backend model
            if (element is Annotation) {
                contents.prune()
                continue
            }
            describeUnsupported(element)?.let {
                constructs += UnsupportedConstruct(element, it)
            }
        }
        return constructs + checkModel(inlinedOxsts, transitionCallTraces)
    }

    protected abstract fun describeUnsupported(element: EObject): String?

    protected open fun checkModel(inlinedOxsts: InlinedOxsts, transitionCallTraces: TransitionCallTraceMap): List<UnsupportedConstruct> {
        return emptyList()
    }

    fun soundness(configured: SoundnessDirection, inlinedOxsts: InlinedOxsts): SoundnessDirection {
        return configured.throughLowering(loweringSoundness(inlinedOxsts))
    }

    protected open fun loweringSoundness(inlinedOxsts: InlinedOxsts): SoundnessDirection {
        return SoundnessDirection.Exact
    }

    object Unlimited : BackendCapabilityChecker() {
        override fun describeUnsupported(element: EObject): String? {
            return null
        }
    }
}
