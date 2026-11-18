/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backend

import hu.bme.mit.semantifyr.oxsts.model.oxsts.Annotation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import org.eclipse.emf.ecore.EObject

class UnsupportedConstruct(
    val element: EObject,
    val description: String,
)

sealed interface ModelSupport {
    fun trusts(verdict: VerificationVerdict, property: Expression): Boolean

    class Supported(
        val soundness: SoundnessDirection,
    ) : ModelSupport {
        override fun trusts(verdict: VerificationVerdict, property: Expression): Boolean {
            return soundness.trusts(verdict, property)
        }
    }

    class Unsupported(
        val constructs: List<UnsupportedConstruct>,
    ) : ModelSupport {
        override fun trusts(verdict: VerificationVerdict, property: Expression): Boolean {
            return false
        }
    }
}

abstract class BackendCapabilityChecker {

    fun support(inlinedOxsts: InlinedOxsts, configured: SoundnessDirection): ModelSupport {
        val unsupported = check(inlinedOxsts)
        if (unsupported.isNotEmpty()) {
            return ModelSupport.Unsupported(unsupported)
        }
        return ModelSupport.Supported(configured.throughLowering(soundness(inlinedOxsts)))
    }

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

    open fun soundness(inlinedOxsts: InlinedOxsts): SoundnessDirection {
        return SoundnessDirection.Exact
    }

    object Unlimited : BackendCapabilityChecker() {
        override fun describeUnsupported(element: EObject): String? {
            return null
        }
    }
}
