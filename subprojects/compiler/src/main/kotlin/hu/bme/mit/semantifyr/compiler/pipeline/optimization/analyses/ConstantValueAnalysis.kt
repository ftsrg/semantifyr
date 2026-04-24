/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.optimization.analyses

import com.google.inject.Inject
import hu.bme.mit.semantifyr.compiler.pipeline.context.EvaluableCompilationContext
import hu.bme.mit.semantifyr.compiler.pipeline.expression.MetaCompileTimeExpressionEvaluator
import hu.bme.mit.semantifyr.compiler.pipeline.expression.MetaCompileTimeExpressionEvaluatorProvider
import hu.bme.mit.semantifyr.compiler.pipeline.expression.evaluateTyped
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.Analysis
import hu.bme.mit.semantifyr.compiler.pipeline.utils.eAllOfType
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.ConstantExpressionEvaluatorProvider
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.EvaluationFailureException
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.ExpressionEvaluation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AssignmentOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.HavocOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration

data class ConstantValueInfo(
    val constants: Map<VariableDeclaration, Expression>,
) {
    fun isConstant(variable: VariableDeclaration): Boolean {
        return variable in constants
    }
    fun valueOf(variable: VariableDeclaration): Expression? {
        return constants[variable]
    }
}

class ConstantValueAnalysis @Inject constructor(
    private val metaCompileTimeExpressionEvaluatorProvider: MetaCompileTimeExpressionEvaluatorProvider,
    private val constantExpressionEvaluatorProvider: ConstantExpressionEvaluatorProvider,
) : Analysis<ConstantValueInfo> {

    override fun compute(input: EvaluableCompilationContext): ConstantValueInfo {
        val evaluator = metaCompileTimeExpressionEvaluatorProvider.getEvaluator(input.rootInstance)
        return ConstantValueComputation(
            input.inlinedOxsts,
            evaluator,
            constantExpressionEvaluatorProvider,
        ).compute()
    }

}

class ConstantValueComputation(
    private val inlinedOxsts: InlinedOxsts,
    private val evaluator: MetaCompileTimeExpressionEvaluator,
    private val constantExpressionEvaluatorProvider: ConstantExpressionEvaluatorProvider,
) {

    fun compute(): ConstantValueInfo {
        val havocedVariables = inlinedOxsts.eAllOfType<HavocOperation>().mapTo(mutableSetOf()) {
            evaluator.evaluateTyped(VariableDeclaration::class.java, it.reference)
        }

        val assignmentsByVariable = inlinedOxsts.eAllOfType<AssignmentOperation>().groupBy {
            evaluator.evaluateTyped(VariableDeclaration::class.java, it.reference)
        }

        val constants = mutableMapOf<VariableDeclaration, Expression>()

        for (variable in inlinedOxsts.eAllOfType<VariableDeclaration>()) {
            if (variable in havocedVariables) {
                continue
            }

            val assignments = assignmentsByVariable[variable]
            if (assignments.isNullOrEmpty()) {
                continue
            }

            val writtenValues = buildList {
                variable.expression?.let {
                    add(it)
                }
                assignments.forEach {
                    add(it.expression)
                }
            }

            val evaluations = writtenValues.map {
                evaluateOrNull(it)
            }
            if (evaluations.any { it == null }) {
                continue
            }
            val unique = evaluations.distinct()
            if (unique.size != 1) {
                continue
            }

            // All writes produce the same constant. Pick any as the canonical value.
            constants[variable] = writtenValues.first()
        }

        return ConstantValueInfo(constants)
    }

    private fun evaluateOrNull(expression: Expression): ExpressionEvaluation? {
        return try {
            constantExpressionEvaluatorProvider.evaluate(expression)
        } catch (_: EvaluationFailureException) {
            null
        }
    }

}
