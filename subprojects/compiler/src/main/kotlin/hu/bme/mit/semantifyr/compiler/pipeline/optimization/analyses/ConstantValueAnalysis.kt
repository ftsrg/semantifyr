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
import hu.bme.mit.semantifyr.compiler.pipeline.expression.tryEvaluateTypedOrNull
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.Analysis
import hu.bme.mit.semantifyr.compiler.pipeline.utils.eAllOfType
import hu.bme.mit.semantifyr.compiler.pipeline.utils.initMustWrite
import hu.bme.mit.semantifyr.compiler.pipeline.utils.variableAssignments
import hu.bme.mit.semantifyr.compiler.pipeline.utils.variableHavocs
import hu.bme.mit.semantifyr.compiler.pipeline.utils.variableReadExpressions
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.ConstantExpressionEvaluatorProvider
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.EvaluationFailureException
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.ExpressionEvaluation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
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

    private val topLevelVariables by lazy {
        inlinedOxsts.variables.toSet()
    }

    private val initReads by lazy {
        listOfNotNull(inlinedOxsts.initTransition).flatMap {
            it.variableReadExpressions(evaluator)
        }.mapNotNull {
            evaluator.tryEvaluateTypedOrNull(VariableDeclaration::class.java, it)
        }.toSet()
    }

    fun compute(): ConstantValueInfo {
        val variableHavocs = inlinedOxsts.variableHavocs(evaluator)
        val variableAssignments = inlinedOxsts.variableAssignments(evaluator)

        val constants = mutableMapOf<VariableDeclaration, Expression>()

        for (variable in inlinedOxsts.eAllOfType<VariableDeclaration>()) {
            if (variable in variableHavocs) {
                continue
            }

            val assignments = variableAssignments[variable]
            if (assignments.isNullOrEmpty()) {
                continue
            }

            val writtenExpressions = buildList {
                if (isDeclarationObservable(variable)) {
                    add(variable.expression)
                }
                assignments.forEach {
                    add(it.expression)
                }
            }

            val writtenValues = writtenExpressions.map {
                evaluateOrNull(it)
            }
            if (writtenValues.any { it == null }) {
                continue
            }
            val unique = writtenValues.distinct()
            if (unique.size != 1) {
                continue
            }

            constants[variable] = writtenExpressions.first()
        }

        return ConstantValueInfo(constants)
    }

    // the declaration valuation is not a state, so a declaration that init must overwrite before reading it is never observed
    private fun isDeclarationObservable(variable: VariableDeclaration): Boolean {
        if (variable !in topLevelVariables) {
            return true
        }
        return variable in initReads || !inlinedOxsts.initMustWrite(variable, evaluator)
    }

    private fun evaluateOrNull(expression: Expression): ExpressionEvaluation? {
        return try {
            constantExpressionEvaluatorProvider.evaluate(expression)
        } catch (_: EvaluationFailureException) {
            null
        }
    }

}
