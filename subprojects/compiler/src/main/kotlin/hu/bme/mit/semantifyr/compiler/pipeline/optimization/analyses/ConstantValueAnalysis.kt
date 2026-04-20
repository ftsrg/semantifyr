/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.optimization.analyses

import com.google.inject.Inject
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.ConstantExpressionEvaluatorProvider
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.ExpressionEvaluation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AssignmentOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.HavocOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration
import hu.bme.mit.semantifyr.compiler.pipeline.context.EvaluableCompilationContext
import hu.bme.mit.semantifyr.compiler.pipeline.expression.MetaStaticExpressionEvaluatorProvider
import hu.bme.mit.semantifyr.compiler.pipeline.expression.evaluateTyped
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.Analysis
import hu.bme.mit.semantifyr.compiler.pipeline.utils.eAllOfType

/**
 * Result of constant-value analysis.
 *
 * [constants] maps each variable known to hold a constant value to an
 * [Expression] that evaluates to that value. The expression is suitable
 * for substituting reads of the variable (after [hu.bme.mit.semantifyr.compiler.pipeline.utils.copy]).
 */
data class ConstantValueInfo(
    val constants: Map<VariableDeclaration, Expression>,
) {
    fun isConstant(variable: VariableDeclaration): Boolean = variable in constants
    fun valueOf(variable: VariableDeclaration): Expression? = constants[variable]
}

/**
 * Identifies variables whose value is always the same compile-time constant.
 *
 * A variable is considered constant if:
 * - it has no [HavocOperation] (havoc introduces non-determinism),
 * - all its written values (initializer plus assignment RHS expressions) are
 *   compile-time evaluable constants, and
 * - all those constants evaluate to the same [ExpressionEvaluation].
 *
 * Variables with no assignments are handled by [VariableLivenessPass]'s
 * substitution rule and are not reported here to avoid double-work.
 */
class ConstantValueAnalysis @Inject constructor(
    private val metaStaticExpressionEvaluatorProvider: MetaStaticExpressionEvaluatorProvider,
    private val constantExpressionEvaluatorProvider: ConstantExpressionEvaluatorProvider,
) : Analysis<ConstantValueInfo> {

    override fun compute(input: EvaluableCompilationContext): ConstantValueInfo {
        val evaluator = metaStaticExpressionEvaluatorProvider.getEvaluator(input.rootInstance)
        val inlinedOxsts = input.inlinedOxsts

        // Variables that have havoc operations can't be constant.
        val havocedVariables = inlinedOxsts.eAllOfType<HavocOperation>()
            .mapTo(mutableSetOf()) { evaluator.evaluateTyped(VariableDeclaration::class.java, it.reference) }

        val assignmentsByVariable = inlinedOxsts.eAllOfType<AssignmentOperation>()
            .groupBy { evaluator.evaluateTyped(VariableDeclaration::class.java, it.reference) }

        val constants = mutableMapOf<VariableDeclaration, Expression>()

        for (variable in inlinedOxsts.eAllOfType<VariableDeclaration>()) {
            if (variable in havocedVariables) continue

            val writtenValues = buildList {
                variable.expression?.let { add(it) }
                assignmentsByVariable[variable]?.forEach { add(it.expression) }
            }

            // Variables with no assignments AND no initializer: unconstrained, skip.
            // Variables with only initializer and no assignments: handled by liveness pass.
            if (writtenValues.isEmpty()) continue
            if (variable.expression != null && assignmentsByVariable[variable].isNullOrEmpty()) continue

            val evaluations = writtenValues.map { evaluateOrNull(it) }
            if (evaluations.any { it == null }) continue
            val unique = evaluations.distinct()
            if (unique.size != 1) continue

            // All writes produce the same constant; pick any RHS as the canonical value.
            constants[variable] = writtenValues.first()
        }

        return ConstantValueInfo(constants)
    }

    private fun evaluateOrNull(expression: Expression): ExpressionEvaluation? {
        return try {
            constantExpressionEvaluatorProvider.evaluate(expression)
        } catch (_: Exception) {
            null
        }
    }

}
