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
import hu.bme.mit.semantifyr.compiler.pipeline.utils.variableReadExpressions
import hu.bme.mit.semantifyr.compiler.pipeline.utils.writeReference
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AssignmentOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.HavocOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Operation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration

data class LivenessInfo(
    val reads: Map<VariableDeclaration, List<Expression>>,
    val assignments: Map<VariableDeclaration, List<Operation>>,
) {
    fun isRead(variable: VariableDeclaration): Boolean {
        return variable in reads
    }
    fun isAssigned(variable: VariableDeclaration): Boolean {
        return variable in assignments
    }
    fun readCount(variable: VariableDeclaration): Int {
        return reads[variable]?.size ?: 0
    }
    fun assignmentCount(variable: VariableDeclaration): Int {
        return assignments[variable]?.size ?: 0
    }
}

class LivenessAnalysis @Inject constructor(
    private val metaCompileTimeExpressionEvaluatorProvider: MetaCompileTimeExpressionEvaluatorProvider,
) : Analysis<LivenessInfo> {

    override fun compute(input: EvaluableCompilationContext): LivenessInfo {
        val evaluator = metaCompileTimeExpressionEvaluatorProvider.getEvaluator(input.rootInstance)
        return LivenessComputation(input.inlinedOxsts, evaluator).compute()
    }

}

class LivenessComputation(
    private val inlinedOxsts: InlinedOxsts,
    private val evaluator: MetaCompileTimeExpressionEvaluator,
) {

    fun compute(): LivenessInfo {
        val reads = inlinedOxsts.variableReadExpressions(evaluator).groupBy {
            evaluator.evaluateTyped(VariableDeclaration::class.java, it)
        }

        val writes = inlinedOxsts.eAllOfType<AssignmentOperation>().map {
            it as Operation
        } + inlinedOxsts.eAllOfType<HavocOperation>().map {
            it as Operation
        }

        val assignments = writes.groupBy {
            evaluator.evaluateTyped(VariableDeclaration::class.java, it.writeReference())
        }

        return LivenessInfo(reads, assignments)
    }

}
