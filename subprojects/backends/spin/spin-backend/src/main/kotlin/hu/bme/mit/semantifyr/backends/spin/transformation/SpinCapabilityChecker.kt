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

private const val WRAPS = "Spin computes with 32-bit integers that wrap without notice, so a value beyond them would make a trace wrong"

class SpinCapabilityChecker @Inject constructor(
    private val builtinSymbolResolver: BuiltinSymbolResolver,
    private val expressionTypeEvaluatorProvider: ExpressionTypeEvaluatorProvider,
    private val integerRangeChecker: IntegerRangeChecker,
) : BackendCapabilityChecker() {

    override fun checkModel(inlinedOxsts: InlinedOxsts): List<UnsupportedConstruct> {
        return integerRangeChecker.check(
            inlinedOxsts = inlinedOxsts,
            unboundedVariables = Limitation.UnboundedIntegerVariables,
            variablesBeyondRange = Limitation.IntegerVariablesBeyond32Bits,
            arithmeticBeyondRange = Limitation.ArithmeticBeyond32Bits,
        )
    }

    override fun describeUnsupported(element: EObject): Limitation? {
        return when (element) {
            is VariableDeclaration -> describeUnsupportedType(element)
            is ForOperation -> Limitation.ForOperations
            is LiteralReal -> Limitation.RealLiterals
            is LiteralString -> Limitation.StringLiterals
            is ArrayLiteral -> Limitation.ArrayLiterals
            is IndexingSuffixExpression -> Limitation.ArrayIndexing
            is IfThenElse -> describeUnsupportedConditional(element)
            else -> null
        }
    }

    private fun describeUnsupportedType(variable: VariableDeclaration): Limitation? {
        return when (variable.typeSpecification?.domain) {
            builtinSymbolResolver.realDatatype(variable) -> Limitation.RealVariables
            builtinSymbolResolver.stringDatatype(variable) -> Limitation.StringVariables
            builtinSymbolResolver.anyDatatype(variable) -> Limitation.AnyVariables
            else -> null
        }
    }

    private fun describeUnsupportedConditional(expression: IfThenElse): Limitation? {
        if (EcoreUtil2.getContainerOfType(expression, TemporalOperator::class.java) == null) {
            return null
        }
        val evaluation = expressionTypeEvaluatorProvider.evaluate(expression) as? ImmutableTypeEvaluation
        if (evaluation?.domainDeclaration === builtinSymbolResolver.boolDatatype(expression)) {
            return null
        }
        return Limitation.NonBooleanConditionalsInProperties
    }

    enum class Limitation(
        override val construct: String,
        override val reason: String,
    ) : BackendLimitation {
        RealVariables("variables of type real", "Promela has integer, boolean and mtype variables only"),
        StringVariables("variables of type string", "Promela has integer, boolean and mtype variables only"),
        AnyVariables("variables of type any", "Promela has integer, boolean and mtype variables only"),
        ForOperations("for-operations", "the Promela translation has no loop"),
        RealLiterals("real literals", "Promela has no real numbers"),
        StringLiterals("string literals", "Promela has no strings"),
        ArrayLiterals("array literals", "the backend receives arrays split into one variable per slot and has no form for an array value"),
        ArrayIndexing("array indexing", "the backend receives arrays split into one variable per slot and has no form for indexing"),
        NonBooleanConditionalsInProperties("non-boolean if-then-else inside a property", "an LTL formula has no conditional expression, and only a boolean one can be rewritten into logic"),
        UnboundedIntegerVariables("unbounded integer variables", "$WRAPS; the property depends on a variable without a static bound"),
        IntegerVariablesBeyond32Bits("integer variables beyond the 32-bit range", "$WRAPS; the property depends on a variable whose static bound exceeds them"),
        ArithmeticBeyond32Bits("arithmetic that may leave the 32-bit range", "$WRAPS; the property depends on the arithmetic"),
    }
}
