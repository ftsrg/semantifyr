/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.optimization.passes

import com.google.inject.Inject
import hu.bme.mit.semantifyr.oxsts.lang.utils.OxstsUtils
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AssignmentOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.HavocOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Operation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.CompilationArtifactManager
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.CompilationPass
import hu.bme.mit.semantifyr.compiler.pipeline.context.EvaluableCompilationContext
import hu.bme.mit.semantifyr.compiler.pipeline.expression.MetaStaticExpressionEvaluatorProvider
import hu.bme.mit.semantifyr.compiler.pipeline.expression.evaluateTyped
import hu.bme.mit.semantifyr.compiler.pipeline.expression.tryEvaluateTypedOrNull
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.AnalysisManager
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationCategory
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationConfig
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.Pass
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.PassResult
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.Worklist
import hu.bme.mit.semantifyr.compiler.pipeline.utils.copy
import hu.bme.mit.semantifyr.compiler.pipeline.utils.eAllOfType
import org.eclipse.xtext.EcoreUtil2
import kotlin.collections.iterator

class VariableLivenessPass @Inject constructor(
    private val config: OptimizationConfig,
    private val metaStaticExpressionEvaluatorProvider: MetaStaticExpressionEvaluatorProvider,
    private val compilationArtifactManager: CompilationArtifactManager,
) : Pass<EvaluableCompilationContext> {

    override fun run(input: EvaluableCompilationContext, analysisManager: AnalysisManager): PassResult {
        if (!config.isEnabled(OptimizationCategory.UnusedVariableElimination)) {
            return PassResult.Unchanged
        }
        val changed = Run(input).execute()
        return if (changed) {
            PassResult.Changed()
        } else {
            PassResult.Unchanged
        }
    }

    private sealed interface WorkItem {
        val variable: VariableDeclaration
        data class Unread(override val variable: VariableDeclaration) : WorkItem
        data class UnassignedInitialized(override val variable: VariableDeclaration) : WorkItem
    }

    private inner class Run(compilation: EvaluableCompilationContext) {
        private val inlinedOxsts = compilation.inlinedOxsts
        private val evaluator = metaStaticExpressionEvaluatorProvider.getEvaluator(compilation.rootInstance)

        private val variableReads: MutableMap<VariableDeclaration, MutableList<Expression>>
        private val variableAssignments: MutableMap<VariableDeclaration, MutableList<Operation>>

        private val worklist = Worklist<WorkItem>()

        init {
            variableReads = inlinedOxsts.eAllOfType<Expression>().filterNot {
                OxstsUtils.isWriteExpression(it)
            }.filter {
                evaluator.tryEvaluateTypedOrNull(VariableDeclaration::class.java, it) != null
            }.groupBy {
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
                evaluator.evaluateTyped(VariableDeclaration::class.java, referenceOfWrite(it))
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
            for (read in readsIn(expression)) {
                val variable = evaluator.evaluateTyped(VariableDeclaration::class.java, read)
                variableReads.getOrPut(variable) {
                    mutableListOf()
                }.add(read)
            }
        }

        private fun dropReadsIn(expression: Expression) {
            val readsByVar = readsIn(expression).groupBy {
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

        private fun referenceOfWrite(operation: Operation): Expression {
            return when (operation) {
                is AssignmentOperation -> operation.reference
                is HavocOperation -> operation.reference
                else -> error("Unexpected write operation: ${operation::class.simpleName}")
            }
        }

        private fun readsIn(expression: Expression): Sequence<Expression> {
            val self = if (
                !OxstsUtils.isWriteExpression(expression) &&
                evaluator.tryEvaluateTypedOrNull(VariableDeclaration::class.java, expression) != null
            ) {
                sequenceOf(expression)
            } else {
                emptySequence()
            }
            val descendants = expression.eAllOfType<Expression>().filterNot {
                OxstsUtils.isWriteExpression(it)
            }.filter {
                evaluator.tryEvaluateTypedOrNull(VariableDeclaration::class.java, it) != null
            }
            return self + descendants
        }
    }
}
