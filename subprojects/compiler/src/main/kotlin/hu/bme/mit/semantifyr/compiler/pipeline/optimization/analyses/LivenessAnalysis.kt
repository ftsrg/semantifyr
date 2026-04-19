/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.optimization.analyses

import com.google.inject.Inject
import hu.bme.mit.semantifyr.oxsts.lang.utils.OxstsUtils
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AssignmentOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.HavocOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Operation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration
import hu.bme.mit.semantifyr.compiler.pipeline.context.InstantiatedCompilationContext
import hu.bme.mit.semantifyr.compiler.pipeline.expression.MetaStaticExpressionEvaluatorProvider
import hu.bme.mit.semantifyr.compiler.pipeline.expression.evaluateTyped
import hu.bme.mit.semantifyr.compiler.pipeline.expression.tryEvaluateTypedOrNull
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.Analysis
import hu.bme.mit.semantifyr.compiler.pipeline.utils.eAllOfType

/**
 * Immutable result of liveness analysis: for each variable declared in the
 * IR, the set of expressions that read it and operations that assign to it.
 */
data class LivenessInfo(
    val reads: Map<VariableDeclaration, List<Expression>>,
    val assignments: Map<VariableDeclaration, List<Operation>>,
) {
    fun isRead(variable: VariableDeclaration): Boolean = variable in reads
    fun isAssigned(variable: VariableDeclaration): Boolean = variable in assignments
    fun readCount(variable: VariableDeclaration): Int = reads[variable]?.size ?: 0
    fun assignmentCount(variable: VariableDeclaration): Int = assignments[variable]?.size ?: 0
}

/**
 * Computes variable liveness information over an [InstantiatedCompilationContext].
 *
 * The analysis walks the IR and resolves navigation expressions via the
 * meta-static evaluator to map them back to their target [VariableDeclaration].
 * Expression reads and assignment operations are grouped into the returned
 * [LivenessInfo].
 *
 * **Note on staleness**: this analysis is a snapshot of the IR at the moment
 * it ran. Any pass that modifies variable reads or assignments must not
 * declare this analysis as preserved; the [hu.bme.mit.semantifyr.compiler.pipeline.optimization.AnalysisManager]
 * will then recompute it on next request.
 */
class LivenessAnalysis @Inject constructor(
    private val metaStaticExpressionEvaluatorProvider: MetaStaticExpressionEvaluatorProvider,
) : Analysis<LivenessInfo> {

    override fun compute(input: InstantiatedCompilationContext): LivenessInfo {
        val evaluator = metaStaticExpressionEvaluatorProvider.getEvaluator(input.instanceTree.rootInstance)
        val inlinedOxsts = input.inlinedOxsts

        val reads = inlinedOxsts.eAllOfType<Expression>()
            .filterNot { OxstsUtils.isWriteExpression(it) }
            .filter { evaluator.tryEvaluateTypedOrNull(VariableDeclaration::class.java, it) != null }
            .groupBy { evaluator.evaluateTyped(VariableDeclaration::class.java, it) }

        val assignments = (
            inlinedOxsts.eAllOfType<AssignmentOperation>()
                .groupBy { evaluator.evaluateTyped(VariableDeclaration::class.java, it.reference) } +
            inlinedOxsts.eAllOfType<HavocOperation>()
                .groupBy { evaluator.evaluateTyped(VariableDeclaration::class.java, it.reference) }
        )

        return LivenessInfo(reads, assignments)
    }

}
