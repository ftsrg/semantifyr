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
import hu.bme.mit.semantifyr.compiler.pipeline.utils.variableReadExpressions
import hu.bme.mit.semantifyr.compiler.pipeline.utils.variableReads
import hu.bme.mit.semantifyr.compiler.pipeline.utils.variableWrites
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
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
        val reads = inlinedOxsts.variableReads(evaluator)
        val writes = inlinedOxsts.variableWrites(evaluator)

        return LivenessInfo(reads, writes)
    }

}
