/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.spin.transformation

import com.google.inject.Inject
import hu.bme.mit.semantifyr.backend.BackendCapabilityChecker
import hu.bme.mit.semantifyr.backend.SoundnessDirection
import hu.bme.mit.semantifyr.backend.UnsupportedConstruct
import hu.bme.mit.semantifyr.backend.transformation.IntegerRangeChecker
import hu.bme.mit.semantifyr.backend.transformation.WholeDelayLowering
import hu.bme.mit.semantifyr.compiler.pipeline.context.TransitionCallTraceMap
import hu.bme.mit.semantifyr.oxsts.lang.library.builtin.BuiltinSymbolResolver
import hu.bme.mit.semantifyr.oxsts.lang.semantics.typesystem.ExpressionTypeEvaluatorProvider
import hu.bme.mit.semantifyr.oxsts.lang.semantics.typesystem.ImmutableTypeEvaluation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ArrayLiteral
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ForOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.IfThenElse
import hu.bme.mit.semantifyr.oxsts.model.oxsts.IndexingSuffixExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralReal
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralString
import hu.bme.mit.semantifyr.oxsts.model.oxsts.TemporalOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration
import org.eclipse.emf.ecore.EObject
import org.eclipse.xtext.EcoreUtil2

class SpinCapabilityChecker @Inject constructor(
    private val builtinSymbolResolver: BuiltinSymbolResolver,
    private val expressionTypeEvaluatorProvider: ExpressionTypeEvaluatorProvider,
    private val integerRangeChecker: IntegerRangeChecker,
    private val wholeDelayLowering: WholeDelayLowering,
    private val spinVariableTransformer: SpinVariableTransformer,
) : BackendCapabilityChecker() {

    override fun checkModel(inlinedOxsts: InlinedOxsts, transitionCallTraces: TransitionCallTraceMap): List<UnsupportedConstruct> {
        return integerRangeChecker.check(inlinedOxsts, transitionCallTraces) + checkWholeDelay(inlinedOxsts)
    }

    private fun checkWholeDelay(inlinedOxsts: InlinedOxsts): List<UnsupportedConstruct> {
        val wholeDelay = wholeDelayLowering.lower(inlinedOxsts) ?: return emptyList()
        return wholeDelay.clocks.filter {
            it.saturation + wholeDelay.longestDelay > Int.MAX_VALUE
        }.map {
            UnsupportedConstruct(it.variable, "clocks that a whole delay may carry beyond the 32-bit range")
        }
    }

    override fun loweringSoundness(inlinedOxsts: InlinedOxsts): SoundnessDirection {
        return wholeDelayLowering.soundness(inlinedOxsts)
    }

    override fun describeUnsupported(element: EObject): String? {
        return when (element) {
            is VariableDeclaration -> describeUnsupportedType(element)
            is ForOperation -> "for-operations"
            is LiteralReal -> "real literals"
            is LiteralString -> "string literals"
            is ArrayLiteral -> "array literals"
            is IndexingSuffixExpression -> "array indexing"
            is IfThenElse -> describeUnsupportedConditional(element)
            else -> null
        }
    }

    private fun describeUnsupportedType(variable: VariableDeclaration): String? {
        val domain = variable.typeSpecification?.domain ?: return null
        if (spinVariableTransformer.kindOf(variable) != null) {
            return null
        }
        return "variables of type ${domain.name}"
    }

    private fun describeUnsupportedConditional(expression: IfThenElse): String? {
        if (EcoreUtil2.getContainerOfType(expression, TemporalOperator::class.java) == null) {
            return null
        }
        val evaluation = expressionTypeEvaluatorProvider.evaluate(expression) as? ImmutableTypeEvaluation
        if (evaluation?.domainDeclaration === builtinSymbolResolver.boolDatatype(expression)) {
            return null
        }
        return "non-boolean if-then-else inside a property"
    }
}
