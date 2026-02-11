/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.semantics.optimization

import com.google.inject.Inject
import hu.bme.mit.semantifyr.oxsts.lang.utils.OxstsUtils
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AssignmentOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Element
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.HavocOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration
import hu.bme.mit.semantifyr.semantics.expression.MetaStaticExpressionEvaluatorProvider
import hu.bme.mit.semantifyr.semantics.expression.evaluateTyped
import hu.bme.mit.semantifyr.semantics.expression.tryEvaluateTypedOrNull
import hu.bme.mit.semantifyr.semantics.transformation.serializer.CompilationStateManager
import hu.bme.mit.semantifyr.semantics.utils.copy
import hu.bme.mit.semantifyr.semantics.utils.eAllOfType
import org.eclipse.xtext.EcoreUtil2

class VariableOptimizer {

    @Inject
    private lateinit var metaStaticExpressionEvaluatorProvider: MetaStaticExpressionEvaluatorProvider

    @Inject
    private lateinit var compilationStateManager: CompilationStateManager

    fun optimize(element: InlinedOxsts): Boolean {
        val optimizer = Optimizer(element)
        return optimizer.optimize(element)
    }

    private inner class Optimizer(
        inlinedOxsts: InlinedOxsts
    ) : AbstractLoopedOptimizer<Element>() {

        val evaluator = metaStaticExpressionEvaluatorProvider.getEvaluator(inlinedOxsts.rootInstance)

        val variableReads = inlinedOxsts.eAllOfType<Expression>().filterNot {
            OxstsUtils.isWriteExpression(it)
        }.filter {
            evaluator.tryEvaluateTypedOrNull(VariableDeclaration::class.java, it) != null
        }.groupByTo(mutableMapOf()) {
            evaluator.evaluateTyped(VariableDeclaration::class.java, it)
        }

        val variableAssignments = (
            inlinedOxsts.eAllOfType<AssignmentOperation>().groupBy {
                evaluator.evaluateTyped(VariableDeclaration::class.java, it.reference)
            } + inlinedOxsts.eAllOfType<HavocOperation>().groupBy {
                evaluator.evaluateTyped(VariableDeclaration::class.java, it.reference)
            }
        ).toMutableMap()

        val unreadVariables: MutableSet<VariableDeclaration>
        val unassignedInitializedVariables: MutableSet<VariableDeclaration>

        init {
            val allVariables = inlinedOxsts.eAllOfType<VariableDeclaration>().toMutableSet()
            val initializedVariables = allVariables.filterTo(mutableSetOf()) {
                it.expression != null
            }
            unreadVariables = (allVariables - variableReads.keys).toMutableSet()
            unassignedInitializedVariables = (initializedVariables - variableAssignments.keys).toMutableSet()
        }

        override fun doOptimizationStep(element: Element): Boolean {
            return removeUnreadVariable()
                || removeUnassignedInitializedVariables()
        }

        private fun removeUnreadVariable(): Boolean {
            val unreadVariable = unreadVariables.firstOrNull()

            if (unreadVariable == null) {
                return false;
            }

            val assignments = variableAssignments[unreadVariable] ?: emptyList()

            for (assignment in assignments) {
                if (assignment is AssignmentOperation) {
                    handleRemovedExpression(assignment.expression)
                }
                EcoreUtil2.remove(assignment)
            }

            EcoreUtil2.remove(unreadVariable)

            variableAssignments -= unreadVariable
            unreadVariables -= unreadVariable

//            compilationStateManager.commitModelState()

            return true
        }

        private fun removeUnassignedInitializedVariables(): Boolean {
            val unassignedInitializedVariable = unassignedInitializedVariables.firstOrNull()

            if (unassignedInitializedVariable == null) {
                return false
            }

            val constantExpression = unassignedInitializedVariable.expression
            val readExpressions = variableReads[unassignedInitializedVariable] ?: emptyList()
            for (readExpression in readExpressions) {
                EcoreUtil2.replace(readExpression, constantExpression.copy())
            }

            unassignedInitializedVariables -= unassignedInitializedVariable
            variableReads -= unassignedInitializedVariable

//            compilationStateManager.commitModelState()

            return true
        }

        private fun handleRemovedExpression(expression: Expression) {
            val reads = expression.eAllOfType<Expression>().filter {
                evaluator.tryEvaluateTypedOrNull(VariableDeclaration::class.java, it) != null
            }.groupBy {
                evaluator.evaluateTyped(VariableDeclaration::class.java, it)
            } as MutableMap

            for (read in reads) {
                val list = variableReads[read.key]
                if (list != null) {
                    list.removeAll(read.value)
                    if (list.isEmpty()) {
                        unreadVariables += read.key
                        variableReads -= read.key
                    }
                }
            }
        }

    }

}
