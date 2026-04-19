/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.optimization.analyses

import com.google.inject.Inject
import hu.bme.mit.semantifyr.oxsts.lang.utils.OxstsUtils
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AssignmentOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AssumptionOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.HavocOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.IfOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Operation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration
import hu.bme.mit.semantifyr.compiler.pipeline.context.InstantiatedCompilationContext
import hu.bme.mit.semantifyr.compiler.pipeline.expression.MetaStaticExpressionEvaluator
import hu.bme.mit.semantifyr.compiler.pipeline.expression.MetaStaticExpressionEvaluatorProvider
import hu.bme.mit.semantifyr.compiler.pipeline.expression.evaluateTyped
import hu.bme.mit.semantifyr.compiler.pipeline.expression.tryEvaluateTypedOrNull
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.Analysis
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.Worklist
import hu.bme.mit.semantifyr.compiler.pipeline.utils.eAllOfType
import org.eclipse.emf.ecore.EObject

/**
 * Result of cone-of-influence analysis.
 *
 * - [relevantVariables]: variables that (transitively) affect the property
 *   expression.
 * - [relevantAssignments]: assignment/havoc operations whose target is in
 *   [relevantVariables]. Assignments to other variables do not affect the
 *   property and may be safely removed.
 *
 * The cone is a **data-dependence** slice: control-flow dependencies (an
 * `assume` or `if` guard that restricts whether a relevant assignment
 * executes) are captured indirectly by including variables read in those
 * guards when the guards contain a relevant assignment in their scope.
 */
data class ConeOfInfluenceInfo(
    val relevantVariables: Set<VariableDeclaration>,
    val relevantAssignments: Set<Operation>,
) {
    fun isRelevant(variable: VariableDeclaration): Boolean = variable in relevantVariables
    fun isRelevant(operation: Operation): Boolean = operation in relevantAssignments
}

/**
 * Computes the set of IR elements that can affect the property expression.
 *
 * Starting from the property, the analysis traces data dependencies
 * backwards: every variable read in the property is relevant, every
 * assignment/havoc targeting a relevant variable is relevant, and every
 * variable read in such an assignment's right-hand side is transitively
 * relevant.
 *
 * Guard expressions (on `if` and `assume` operations containing relevant
 * assignments) are also traced, so control dependencies of relevant writes
 * are captured.
 */
class ConeOfInfluenceAnalysis @Inject constructor(
    private val metaStaticExpressionEvaluatorProvider: MetaStaticExpressionEvaluatorProvider,
) : Analysis<ConeOfInfluenceInfo> {

    override fun compute(input: InstantiatedCompilationContext): ConeOfInfluenceInfo {
        val evaluator = metaStaticExpressionEvaluatorProvider.getEvaluator(input.instanceTree.rootInstance)
        val inlinedOxsts = input.inlinedOxsts

        // Pre-index: for each variable, the operations that assign to it.
        val assignmentsByVariable: Map<VariableDeclaration, List<Operation>> = (
            inlinedOxsts.eAllOfType<AssignmentOperation>()
                .groupBy { evaluator.evaluateTyped(VariableDeclaration::class.java, it.reference) } +
            inlinedOxsts.eAllOfType<HavocOperation>()
                .groupBy { evaluator.evaluateTyped(VariableDeclaration::class.java, it.reference) }
        )

        val relevantVariables = mutableSetOf<VariableDeclaration>()
        val relevantAssignments = mutableSetOf<Operation>()

        // Worklist of variables to analyze - when a variable enters the cone,
        // its assignments (and their enclosing guards) also enter the cone,
        // and their read dependencies propagate backward.
        val variableWorklist = Worklist<VariableDeclaration>()

        // Seed from the property expression.
        for (variable in variablesReadIn(inlinedOxsts.property.expression, evaluator)) {
            if (relevantVariables.add(variable)) variableWorklist.add(variable)
        }

        while (variableWorklist.isNotEmpty()) {
            val variable = variableWorklist.pop()

            for (assignment in assignmentsByVariable[variable] ?: emptyList()) {
                if (!relevantAssignments.add(assignment)) continue

                // Data dependence: variables read by the assignment's RHS.
                val reads = when (assignment) {
                    is AssignmentOperation -> variablesReadIn(assignment.expression, evaluator)
                    is HavocOperation -> emptySet()
                    else -> emptySet()
                }
                for (read in reads) {
                    if (relevantVariables.add(read)) variableWorklist.add(read)
                }

                // Control dependence: guards of enclosing `if`/`assume` that
                // gate whether this assignment executes.
                for (guardVariable in guardVariablesFor(assignment, evaluator)) {
                    if (relevantVariables.add(guardVariable)) variableWorklist.add(guardVariable)
                }
            }
        }

        return ConeOfInfluenceInfo(
            relevantVariables = relevantVariables,
            relevantAssignments = relevantAssignments,
        )
    }

    /** Variables appearing as reads inside [expression] (including [expression] itself if it is a read). */
    private fun variablesReadIn(
        expression: Expression,
        evaluator: MetaStaticExpressionEvaluator,
    ): Set<VariableDeclaration> {
        val result = mutableSetOf<VariableDeclaration>()
        val candidates = sequenceOf(expression) + expression.eAllOfType<Expression>()
        for (candidate in candidates) {
            if (OxstsUtils.isWriteExpression(candidate)) continue
            val variable = evaluator.tryEvaluateTypedOrNull(VariableDeclaration::class.java, candidate)
            if (variable != null) result += variable
        }
        return result
    }

    /** Variables read in the guard expressions of `if`/`assume` operations enclosing [operation]. */
    private fun guardVariablesFor(
        operation: Operation,
        evaluator: MetaStaticExpressionEvaluator,
    ): Set<VariableDeclaration> {
        val result = mutableSetOf<VariableDeclaration>()
        var current: EObject? = operation.eContainer()
        while (current != null) {
            when (current) {
                is IfOperation -> result += variablesReadIn(current.guard, evaluator)
                is AssumptionOperation -> result += variablesReadIn(current.expression, evaluator)
            }
            current = current.eContainer()
        }
        return result
    }

}
