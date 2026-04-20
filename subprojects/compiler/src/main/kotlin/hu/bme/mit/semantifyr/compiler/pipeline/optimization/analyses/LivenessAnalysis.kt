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
import hu.bme.mit.semantifyr.compiler.pipeline.context.EvaluableCompilationContext
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
 * Computes variable liveness information over an [EvaluableCompilationContext].
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

    override fun compute(input: EvaluableCompilationContext): LivenessInfo {
        val evaluator = metaStaticExpressionEvaluatorProvider.getEvaluator(input.rootInstance)
        val inlinedOxsts = input.inlinedOxsts

        val reads = inlinedOxsts.eAllOfType<Expression>()
            .filterNot { OxstsUtils.isWriteExpression(it) }
            .filter { evaluator.tryEvaluateTypedOrNull(VariableDeclaration::class.java, it) != null }
            .groupBy { evaluator.evaluateTyped(VariableDeclaration::class.java, it) }

        // Concat before groupBy - using Map.plus would silently drop one side's
        // entries when a variable has both assignments and havocs.
        val writes: Sequence<Operation> =
            inlinedOxsts.eAllOfType<AssignmentOperation>().map { it as Operation } +
                inlinedOxsts.eAllOfType<HavocOperation>().map { it as Operation }
        val assignments = writes.groupBy {
            evaluator.evaluateTyped(VariableDeclaration::class.java, referenceOf(it))
        }

        return LivenessInfo(reads, assignments)
    }

    private fun referenceOf(operation: Operation): Expression = when (operation) {
        is AssignmentOperation -> operation.reference
        is HavocOperation -> operation.reference
        else -> error("Unexpected write operation: ${operation::class.simpleName}")
    }

}
