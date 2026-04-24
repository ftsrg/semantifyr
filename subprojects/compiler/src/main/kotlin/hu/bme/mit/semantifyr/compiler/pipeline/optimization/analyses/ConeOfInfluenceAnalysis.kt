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
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.Worklist
import hu.bme.mit.semantifyr.compiler.pipeline.utils.eAllOfType
import hu.bme.mit.semantifyr.compiler.pipeline.utils.variableReadExpressions
import hu.bme.mit.semantifyr.compiler.pipeline.utils.writeReference
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AssignmentOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AssumptionOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.HavocOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.IfOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Operation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.TransitionDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration
import org.eclipse.emf.ecore.EObject

/**
 * Variable reads and writes that are relevant to the 'prop' expression
 */
data class ConeOfInfluenceInfo(
    val relevantVariables: Set<VariableDeclaration>,
    val relevantAssignments: Set<Operation>,
) {

    fun isRelevant(variable: VariableDeclaration): Boolean {
        return variable in relevantVariables
    }

    fun isRelevant(operation: Operation): Boolean {
        return operation in relevantAssignments
    }

}

class ConeOfInfluenceAnalysis @Inject constructor(
    private val metaCompileTimeExpressionEvaluatorProvider: MetaCompileTimeExpressionEvaluatorProvider,
) : Analysis<ConeOfInfluenceInfo> {

    override fun compute(input: EvaluableCompilationContext): ConeOfInfluenceInfo {
        val evaluator = metaCompileTimeExpressionEvaluatorProvider.getEvaluator(input.rootInstance)
        return ConeOfInfluenceComputation(input.inlinedOxsts, evaluator).compute()
    }

}

class ConeOfInfluenceComputation(
    private val inlinedOxsts: InlinedOxsts,
    private val evaluator: MetaCompileTimeExpressionEvaluator,
) {

    fun compute(): ConeOfInfluenceInfo {
        val allWrites = inlinedOxsts.eAllOfType<AssignmentOperation>().map {
            it as Operation
        } + inlinedOxsts.eAllOfType<HavocOperation>().map {
            it as Operation
        }

        val assignmentsByVariable = allWrites.groupBy {
            evaluator.evaluateTyped(VariableDeclaration::class.java, it.writeReference())
        }

        val relevantVariables = mutableSetOf<VariableDeclaration>()
        val relevantAssignments = mutableSetOf<Operation>()

        val variableWorklist = Worklist<VariableDeclaration>()

        for (variable in variablesReadIn(inlinedOxsts.property.expression)) {
            if (relevantVariables.add(variable)) {
                variableWorklist.add(variable)
            }
        }

        while (variableWorklist.isNotEmpty()) {
            val variable = variableWorklist.pop()
            val assignments = assignmentsByVariable[variable] ?: emptyList()

            for (assignment in assignments) {
                if (!relevantAssignments.add(assignment)) {
                    continue
                }

                val reads = when (assignment) {
                    is AssignmentOperation -> variablesReadIn(assignment.expression)
                    is HavocOperation -> emptySet()
                    else -> error("Unexpected write operation: ${assignment::class.simpleName}")
                }

                for (read in reads) {
                    if (relevantVariables.add(read)) {
                        variableWorklist.add(read)
                    }
                }

                for (guardVariable in guardVariablesFor(assignment)) {
                    if (relevantVariables.add(guardVariable)) {
                        variableWorklist.add(guardVariable)
                    }
                }
            }
        }

        return ConeOfInfluenceInfo(
            relevantVariables = relevantVariables.toSet(),
            relevantAssignments = relevantAssignments.toSet(),
        )
    }

    private fun variablesReadIn(expression: Expression): Set<VariableDeclaration> {
        return expression.variableReadExpressions(evaluator).mapTo(mutableSetOf()) {
            evaluator.evaluateTyped(VariableDeclaration::class.java, it)
        }
    }

    private fun guardVariablesFor(operation: Operation): Set<VariableDeclaration> {
        val result = mutableSetOf<VariableDeclaration>()

        var current = operation.eContainer()
        while (current != null) {
            when (current) {
                is IfOperation -> result += variablesReadIn(current.guard)
                is AssumptionOperation -> result += variablesReadIn(current.expression)
            }
            current = current.eContainer()
        }

        val transitionBody = transitionBodyContaining(operation) ?: return result
        for (assume in transitionBody.eAllOfType<AssumptionOperation>()) {
            if (assume === operation) {
                continue
            }
            result += variablesReadIn(assume.expression)
        }
        return result
    }

    private fun transitionBodyContaining(operation: Operation): EObject? {
        var child: EObject = operation
        var current: EObject? = operation.eContainer()
        while (current != null) {
            if (current is TransitionDeclaration) {
                return child
            }
            child = current
            current = current.eContainer()
        }
        return null
    }

}
