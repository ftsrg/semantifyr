/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.uppaal.transformation

import com.google.inject.Inject
import hu.bme.mit.semantifyr.backend.BackendCapabilityChecker
import hu.bme.mit.semantifyr.backend.UnsupportedConstruct
import hu.bme.mit.semantifyr.backend.transformation.IntegerRangeChecker
import hu.bme.mit.semantifyr.oxsts.lang.semantics.clock.ClockTestAnalysis
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.ConstantExpressionEvaluatorProvider
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.EvaluationFailureException
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.IntegerEvaluation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ArrayLiteral
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AssignmentOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AssumptionOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.BooleanOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ComparisonOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ForOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.IfOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.IfThenElse
import hu.bme.mit.semantifyr.oxsts.model.oxsts.IndexingSuffixExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralString
import hu.bme.mit.semantifyr.oxsts.model.oxsts.NegationOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration
import org.eclipse.emf.ecore.EObject

// verifyta refuses a clock constraint whose constant has a larger magnitude
const val UPPAAL_CLOCK_BOUND_LIMIT = 1073741822

open class UppaalCapabilityChecker @Inject constructor(
    private val integerRangeChecker: IntegerRangeChecker,
    private val uppaalDeclarationsBuilder: UppaalDeclarationsBuilder,
    protected val uppaalVariableTransformer: UppaalVariableTransformer,
    private val clockTestAnalysis: ClockTestAnalysis,
    private val constantExpressionEvaluatorProvider: ConstantExpressionEvaluatorProvider,
) : BackendCapabilityChecker() {

    override fun checkModel(inlinedOxsts: InlinedOxsts): List<UnsupportedConstruct> {
        return integerRangeChecker.checkArithmetic(inlinedOxsts) {
            uppaalDeclarationsBuilder.declaredRangeOf(it, inlinedOxsts)
        }
    }

    override fun describeUnsupported(element: EObject): String? {
        return when (element) {
            is VariableDeclaration -> describeUnsupportedType(element)
            is ForOperation -> "for-operations"
            is LiteralString -> "string literals"
            is ArrayLiteral -> "array literals"
            is IndexingSuffixExpression -> "array indexing"
            is ComparisonOperator -> describeClockBound(element)
            is Expression -> describeClockRead(element)
            else -> null
        }
    }

    private fun describeUnsupportedType(variable: VariableDeclaration): String? {
        val domain = variable.typeSpecification?.domain ?: return null
        if (uppaalVariableTransformer.kindOf(variable) != null) {
            return null
        }
        return "variables of type ${domain.name}"
    }

    private fun describeClockBound(comparison: ComparisonOperator): String? {
        val test = clockTestAnalysis.clockTest(comparison) ?: return null
        val evaluation = try {
            constantExpressionEvaluatorProvider.evaluate(test.bound)
        } catch (_: EvaluationFailureException) {
            return null
        }
        val bound = (evaluation as? IntegerEvaluation)?.value ?: return null
        if (bound in -UPPAAL_CLOCK_BOUND_LIMIT..UPPAAL_CLOCK_BOUND_LIMIT) {
            return null
        }
        return "clock bounds beyond $UPPAAL_CLOCK_BOUND_LIMIT"
    }

    private fun describeClockRead(expression: Expression): String? {
        if (clockTestAnalysis.referencedClock(expression) == null || isReset(expression) || isGuardTest(expression)) {
            return null
        }
        return "clocks read outside the conditions of assumptions and ifs"
    }

    private fun isReset(expression: Expression): Boolean {
        return (expression.eContainer() as? AssignmentOperation)?.reference === expression
    }

    private fun isGuardTest(read: Expression): Boolean {
        var clockSide: EObject = read
        var parent = clockSide.eContainer()
        while (parent is IfThenElse && parent.guard !== clockSide) {
            clockSide = parent
            parent = clockSide.eContainer()
        }
        val comparison = parent as? ComparisonOperator ?: return false
        val test = clockTestAnalysis.clockTest(comparison) ?: return false
        if (test.clock !== clockSide || test.clockLeaves == null) {
            return false
        }
        return isGuard(comparison)
    }

    private fun isGuard(condition: Expression): Boolean {
        var current: EObject = condition
        while (true) {
            val parent = current.eContainer()
            when {
                parent is AssumptionOperation -> {
                    return true
                }
                parent is IfOperation -> {
                    return parent.guard === current
                }
                parent is NegationOperator || parent is BooleanOperator || parent is IfThenElse -> {
                    current = parent
                }
                parent is ComparisonOperator && clockTestAnalysis.clockTest(parent)?.clock === current -> {
                    current = parent
                }
                else -> {
                    return false
                }
            }
        }
    }
}
