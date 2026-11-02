/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.spin.transformation

import com.google.inject.Inject
import hu.bme.mit.semantifyr.backend.BackendCapabilityChecker
import hu.bme.mit.semantifyr.backend.BackendLimitation
import hu.bme.mit.semantifyr.backend.UnsupportedConstruct
import hu.bme.mit.semantifyr.backend.transformation.IntegerRangeChecker
import hu.bme.mit.semantifyr.oxsts.lang.library.builtin.BuiltinSymbolResolver
import hu.bme.mit.semantifyr.oxsts.lang.semantics.typesystem.ExpressionTypeEvaluatorProvider
import hu.bme.mit.semantifyr.oxsts.lang.semantics.typesystem.ImmutableTypeEvaluation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ArrayLiteral
import hu.bme.mit.semantifyr.oxsts.model.oxsts.EnumDeclaration
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
) : BackendCapabilityChecker() {

    override fun checkModel(inlinedOxsts: InlinedOxsts): List<UnsupportedConstruct> {
        return integerRangeChecker.check(inlinedOxsts)
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
        if (domain is EnumDeclaration || domain === builtinSymbolResolver.intDatatype(variable) || domain === builtinSymbolResolver.boolDatatype(variable)) {
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

    companion object {
        private const val WRAPS = "Spin computes with 32-bit integers that wrap without notice, so a value beyond them would make a trace wrong"

        val limitations = listOf(
            BackendLimitation("variables of type real", "Promela has integer, boolean and mtype variables only"),
            BackendLimitation("variables of type string", "Promela has integer, boolean and mtype variables only"),
            BackendLimitation("for-operations", "the Promela translation has no loop"),
            BackendLimitation("real literals", "Promela has no real numbers"),
            BackendLimitation("string literals", "Promela has no strings"),
            BackendLimitation("array literals", "the backend receives arrays split into one variable per slot and has no form for an array value"),
            BackendLimitation("array indexing", "the backend receives arrays split into one variable per slot and has no form for indexing"),
            BackendLimitation("non-boolean if-then-else inside a property", "an LTL formula has no conditional expression, and only a boolean one can be rewritten into logic"),
            BackendLimitation("unbounded integer variables", "$WRAPS; the property depends on a variable without a static bound"),
            BackendLimitation("integer variables beyond the 32-bit range", "$WRAPS; the property depends on a variable whose static bound exceeds them"),
            BackendLimitation("arithmetic that may leave the 32-bit range", "$WRAPS; the property depends on the arithmetic"),
        )
    }
}
