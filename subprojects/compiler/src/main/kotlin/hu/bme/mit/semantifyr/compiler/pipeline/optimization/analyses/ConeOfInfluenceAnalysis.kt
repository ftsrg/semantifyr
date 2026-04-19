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
import hu.bme.mit.semantifyr.oxsts.model.oxsts.SequenceOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.TransitionDeclaration
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
        // We must concat the write lists before groupBy - using Map.plus would
        // silently drop one list when a variable has both assignments and havocs.
        val allWrites: Sequence<Operation> =
            inlinedOxsts.eAllOfType<AssignmentOperation>().map { it as Operation } +
                inlinedOxsts.eAllOfType<HavocOperation>().map { it as Operation }
        val assignmentsByVariable: Map<VariableDeclaration, List<Operation>> = allWrites
            .groupBy { evaluator.evaluateTyped(VariableDeclaration::class.java, referenceOfWrite(it)) }

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

    private fun referenceOfWrite(operation: Operation): Expression = when (operation) {
        is AssignmentOperation -> operation.reference
        is HavocOperation -> operation.reference
        else -> error("Unexpected write operation: ${operation::class.simpleName}")
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

    /**
     * Variables read in the guards that decide whether [operation] commits.
     *
     * Under OXSTS's atomic-transition semantics, a transition body commits
     * as a unit: if **any** `assume` in the body fails, every write in the
     * body is rolled back. So every `assume` in the enclosing transition is
     * a control dependency of every write in that transition - including
     * assumes that textually follow the write, at any nesting depth.
     *
     * Concretely we collect:
     *  - [IfOperation.guard] on every enclosing `if` (the write only runs on
     *    one arm; the guard decides which).
     *  - [AssumptionOperation.expression] on every enclosing `assume`.
     *  - The expression of every [AssumptionOperation] anywhere inside the
     *    containing transition body (sibling assumes at any position, at any
     *    nesting depth). This captures the atomic-commit control dep.
     */
    private fun guardVariablesFor(
        operation: Operation,
        evaluator: MetaStaticExpressionEvaluator,
    ): Set<VariableDeclaration> {
        val result = mutableSetOf<VariableDeclaration>()

        // Ancestor if/assume guards.
        var current: EObject? = operation.eContainer()
        while (current != null) {
            when (current) {
                is IfOperation -> result += variablesReadIn(current.guard, evaluator)
                is AssumptionOperation -> result += variablesReadIn(current.expression, evaluator)
            }
            current = current.eContainer()
        }

        // Atomic commit: every assume anywhere in the enclosing transition
        // body is a control dep, even if it textually follows the write.
        val transitionBody = transitionBodyContaining(operation) ?: return result
        for (assume in transitionBody.eAllOfType<AssumptionOperation>()) {
            if (assume === operation) continue
            result += variablesReadIn(assume.expression, evaluator)
        }
        return result
    }

    /**
     * The top-level body of the transition containing [operation], or `null`
     * if [operation] is not within a transition. The returned object is the
     * child of the [TransitionDeclaration] that contains [operation] - every
     * op inside it commits-or-rolls-back as one atomic unit.
     */
    private fun transitionBodyContaining(operation: Operation): EObject? {
        var child: EObject = operation
        var current: EObject? = operation.eContainer()
        while (current != null) {
            if (current is TransitionDeclaration) return child
            child = current
            current = current.eContainer()
        }
        return null
    }

}
