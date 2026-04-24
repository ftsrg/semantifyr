/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.optimization.passes

import com.google.inject.Inject
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.CompilationArtifactManager
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.CompilationPass
import hu.bme.mit.semantifyr.compiler.pipeline.context.EvaluableCompilationContext
import hu.bme.mit.semantifyr.compiler.pipeline.expression.MetaCompileTimeExpressionEvaluator
import hu.bme.mit.semantifyr.compiler.pipeline.expression.MetaCompileTimeExpressionEvaluatorProvider
import hu.bme.mit.semantifyr.compiler.pipeline.expression.evaluateTyped
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.AnalysisManager
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationCategory
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationConfig
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.Pass
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.PassResult
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.Worklist
import hu.bme.mit.semantifyr.compiler.pipeline.utils.copy
import hu.bme.mit.semantifyr.compiler.pipeline.utils.eAllOfType
import hu.bme.mit.semantifyr.compiler.pipeline.utils.variableReadExpressions
import hu.bme.mit.semantifyr.compiler.pipeline.utils.writeReference
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AssignmentOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.HavocOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Operation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration
import org.eclipse.xtext.EcoreUtil2

class VariableLivenessPass @Inject constructor(
    private val config: OptimizationConfig,
    private val metaCompileTimeExpressionEvaluatorProvider: MetaCompileTimeExpressionEvaluatorProvider,
    private val compilationArtifactManager: CompilationArtifactManager,
) : Pass<EvaluableCompilationContext> {

    override fun run(input: EvaluableCompilationContext, analysisManager: AnalysisManager): PassResult {
        if (!config.isEnabled(OptimizationCategory.UnusedVariableElimination)) {
            return PassResult.Unchanged
        }
        val evaluator = metaCompileTimeExpressionEvaluatorProvider.getEvaluator(input.rootInstance)
        val changed = VariableLivenessComputation(input.inlinedOxsts, evaluator, compilationArtifactManager).execute()
        return if (changed) {
            PassResult.Changed()
        } else {
            PassResult.Unchanged
        }
    }

}

class VariableLivenessComputation(
    private val inlinedOxsts: InlinedOxsts,
    private val evaluator: MetaCompileTimeExpressionEvaluator,
    private val compilationArtifactManager: CompilationArtifactManager,
) {

    private sealed interface WorkItem {
        val variable: VariableDeclaration
        data class Unread(override val variable: VariableDeclaration) : WorkItem
        data class UnassignedInitialized(override val variable: VariableDeclaration) : WorkItem
    }

    private val variableReads: MutableMap<VariableDeclaration, MutableList<Expression>>
    private val variableAssignments: MutableMap<VariableDeclaration, MutableList<Operation>>

    private val worklist = Worklist<WorkItem>()

    init {
        variableReads = inlinedOxsts.variableReadExpressions(evaluator).groupBy {
            evaluator.evaluateTyped(VariableDeclaration::class.java, it)
        }.mapValuesTo(mutableMapOf()) {
            it.value.toMutableList()
        }

        // Concat before groupBy - using Map.plus would silently drop one side's
        // entries when a variable has both assignments and havocs.
        val writes: Sequence<Operation> =
            inlinedOxsts.eAllOfType<AssignmentOperation>().map { it as Operation } +
                inlinedOxsts.eAllOfType<HavocOperation>().map { it as Operation }
        variableAssignments = writes.groupBy {
            evaluator.evaluateTyped(VariableDeclaration::class.java, it.writeReference())
        }.mapValuesTo(mutableMapOf()) {
            it.value.toMutableList()
        }

        val allVariables = inlinedOxsts.eAllOfType<VariableDeclaration>().toSet()

        (allVariables - variableReads.keys).forEach {
            worklist.add(WorkItem.Unread(it))
        }
        allVariables.filter {
            it.expression != null && it !in variableAssignments
        }.forEach {
            worklist.add(WorkItem.UnassignedInitialized(it))
        }
    }

    fun execute(): Boolean {
        var didAnyWork = false
        while (worklist.isNotEmpty()) {
            val item = worklist.pop()
            if (process(item)) {
                didAnyWork = true
            }
        }
        return didAnyWork
    }

    private fun process(item: WorkItem): Boolean {
        return when (item) {
            is WorkItem.Unread -> removeUnread(item.variable)
            is WorkItem.UnassignedInitialized -> substituteUnassignedInitialized(item.variable)
        }
    }

    private fun removeUnread(variable: VariableDeclaration): Boolean {
        val assignments = variableAssignments[variable] ?: emptyList()
        for (assignment in assignments) {
            if (assignment is AssignmentOperation) {
                dropReadsIn(assignment.expression)
            }
            EcoreUtil2.remove(assignment)
        }
        EcoreUtil2.remove(variable)
        variableAssignments -= variable
        compilationArtifactManager.commitStep(CompilationPass.UnusedVariableElimination)
        return true
    }

    private fun substituteUnassignedInitialized(variable: VariableDeclaration): Boolean {
        val constantExpression = variable.expression
        val reads = variableReads[variable] ?: return false
        for (readExpression in reads) {
            val substitute = constantExpression.copy()
            EcoreUtil2.replace(readExpression, substitute)
            trackReadsIn(substitute)
        }
        variableReads -= variable
        worklist.add(WorkItem.Unread(variable))
        compilationArtifactManager.commitStep(CompilationPass.UnusedVariableElimination)
        return true
    }

    private fun trackReadsIn(expression: Expression) {
        for (read in expression.variableReadExpressions(evaluator)) {
            val variable = evaluator.evaluateTyped(VariableDeclaration::class.java, read)
            variableReads.getOrPut(variable) {
                mutableListOf()
            }.add(read)
        }
    }

    private fun dropReadsIn(expression: Expression) {
        val readsByVar = expression.variableReadExpressions(evaluator).groupBy {
            evaluator.evaluateTyped(VariableDeclaration::class.java, it)
        }

        for ((variable, dropped) in readsByVar) {
            val list = variableReads[variable] ?: continue
            list.removeAll(dropped)
            if (list.isEmpty()) {
                variableReads -= variable
                worklist.add(WorkItem.Unread(variable))
            }
        }
    }

}
