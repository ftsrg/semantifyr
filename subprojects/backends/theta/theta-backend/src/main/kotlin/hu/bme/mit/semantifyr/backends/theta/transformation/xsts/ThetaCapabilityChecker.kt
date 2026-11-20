/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.theta.transformation.xsts

import com.google.inject.Inject
import hu.bme.mit.semantifyr.backend.BackendCapabilityChecker
import hu.bme.mit.semantifyr.backend.SoundnessDirection
import hu.bme.mit.semantifyr.backend.UnsupportedConstruct
import hu.bme.mit.semantifyr.backend.transformation.WholeDelayLowering
import hu.bme.mit.semantifyr.compiler.pipeline.context.TransitionCallTraceMap
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ArrayLiteral
import hu.bme.mit.semantifyr.oxsts.model.oxsts.DataTypeDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ForOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralReal
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralString
import hu.bme.mit.semantifyr.oxsts.model.oxsts.RangeExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration
import org.eclipse.emf.ecore.EObject

open class ThetaCapabilityChecker @Inject constructor(
    private val thetaDomainTransformer: ThetaDomainTransformer,
    private val wholeDelayLowering: WholeDelayLowering,
) : BackendCapabilityChecker() {

    override fun checkModel(inlinedOxsts: InlinedOxsts, transitionCallTraces: TransitionCallTraceMap): List<UnsupportedConstruct> {
        val wholeDelay = wholeDelayLowering.lower(inlinedOxsts) ?: return emptyList()
        return wholeDelay.clocks.filter {
            it.saturation > Int.MAX_VALUE
        }.map {
            UnsupportedConstruct(it.variable, "clocks bounded at the largest 32-bit integer")
        }
    }

    override fun loweringSoundness(inlinedOxsts: InlinedOxsts): SoundnessDirection {
        return wholeDelayLowering.soundness(inlinedOxsts)
    }

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
        if (thetaDomainTransformer.typeOf(domain) != null) {
            return null
        }
        return "variables of type ${domain.name}"
    }
}
