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
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ChoiceOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ForOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.HavocOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.IfOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LocalVarDeclarationOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Operation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.SequenceOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.TraceOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.TransitionDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration
import hu.bme.mit.semantifyr.compiler.pipeline.context.InstantiatedCompilationContext
import hu.bme.mit.semantifyr.compiler.pipeline.expression.MetaStaticExpressionEvaluator
import hu.bme.mit.semantifyr.compiler.pipeline.expression.MetaStaticExpressionEvaluatorProvider
import hu.bme.mit.semantifyr.compiler.pipeline.expression.evaluateTyped
import hu.bme.mit.semantifyr.compiler.pipeline.expression.tryEvaluateTypedOrNull
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.Analysis
import hu.bme.mit.semantifyr.compiler.pipeline.utils.eAllOfType
import kotlin.collections.iterator

/**
 * Result of reaching-definitions analysis: for each variable read expression,
 * the set of writing operations (assignments or havocs) that could have
 * produced its value at that program point.
 *
 * An empty set means the read is "uninitialized" (only possible for local
 * variables; global variables have initializers or havocs at the entry).
 * A set with one element means the value is uniquely determined by that
 * write, enabling copy propagation.
 */
data class ReachingDefinitionsInfo(
    val defsOf: Map<Expression, Set<Operation>>,
)

/**
 * Computes reaching definitions via a structured tree walk (no explicit CFG).
 *
 * Semantics (per transition):
 * - Sequence: the first step's INs are the transition's INs; each subsequent
 *   step's INs are the previous step's OUTs.
 * - Choice: all branches start with the same INs; OUTs are the union of
 *   branches' OUTs.
 * - If: body and else each start with the same INs; OUTs are union of body OUTs
 *   and (else OUTs or the original INs, if no else).
 * - For: body INs include the transition INs plus the body's own OUTs (loop).
 *   Compute via fixed point. OUTs are body OUTs plus original INs (loop may
 *   execute zero times).
 *
 * Global-scope reads (outside any transition) get all definitions in the
 * entire IR that target their variable. Only within-transition reads benefit
 * from structured RD.
 */
class ReachingDefinitionsAnalysis @Inject constructor(
    private val metaStaticExpressionEvaluatorProvider: MetaStaticExpressionEvaluatorProvider,
) : Analysis<ReachingDefinitionsInfo> {

    override fun compute(input: InstantiatedCompilationContext): ReachingDefinitionsInfo {
        val evaluator = metaStaticExpressionEvaluatorProvider.getEvaluator(input.instanceTree.rootInstance)
        val inlinedOxsts = input.inlinedOxsts

        // Initial definitions: the global initial state = every variable's
        // initializer assignment (if any), plus the first transition's effects.
        // We approximate by treating all writes to a variable at the IR root
        // as the initial incoming set. This is conservative but safe.
        val allWrites = (
            inlinedOxsts.eAllOfType<AssignmentOperation>().map { it as Operation } +
            inlinedOxsts.eAllOfType<HavocOperation>().map { it as Operation }
        ).toSet()
        val initialIn = allWrites.groupBy { variableOfWrite(it, evaluator) }
            .mapValues { it.value.toSet() }

        val defsOf = mutableMapOf<Expression, Set<Operation>>()

        // Analyse each transition independently - assignments between transitions
        // are joined via the conservative initialIn.
        for (transition in transitionsOf(inlinedOxsts)) {
            for (branch in transition.branches) {
                walkOperation(branch, initialIn, evaluator, defsOf)
            }
        }

        // Reads outside any transition (e.g., property expression, variable
        // initializers) see the full set of writes as reaching them.
        val visitedReads = defsOf.keys
        for (read in readsIn(inlinedOxsts, evaluator)) {
            if (read in visitedReads) continue
            val variable = evaluator.tryEvaluateTypedOrNull(VariableDeclaration::class.java, read) ?: continue
            defsOf[read] = initialIn[variable].orEmpty()
        }

        return ReachingDefinitionsInfo(defsOf)
    }

    /**
     * Walks [operation] with incoming reaching definitions [incoming].
     * Populates [defsOf] for each read encountered.
     * Returns the outgoing reaching definitions after [operation] executes.
     */
    private fun walkOperation(
        operation: Operation,
        incoming: Map<VariableDeclaration, Set<Operation>>,
        evaluator: MetaStaticExpressionEvaluator,
        defsOf: MutableMap<Expression, Set<Operation>>,
    ): Map<VariableDeclaration, Set<Operation>> = when (operation) {
        is SequenceOperation -> {
            var state = incoming
            for (step in operation.steps) {
                state = walkOperation(step, state, evaluator, defsOf)
            }
            state
        }
        is ChoiceOperation -> {
            // All branches see `incoming`; merge outputs.
            operation.branches
                .map { walkOperation(it, incoming, evaluator, defsOf) }
                .fold(emptyMap<VariableDeclaration, Set<Operation>>()) { acc, branchOut -> mergeDefs(acc, branchOut) }
        }
        is IfOperation -> {
            recordReads(operation.guard, incoming, evaluator, defsOf)
            val bodyOut = walkOperation(operation.body, incoming, evaluator, defsOf)
            val elseOut = operation.`else`?.let { walkOperation(it, incoming, evaluator, defsOf) } ?: incoming
            mergeDefs(bodyOut, elseOut)
        }
        is ForOperation -> {
            recordReads(operation.rangeExpression, incoming, evaluator, defsOf)
            // Fixed point: body may execute 0 or more times. Each iteration
            // sees previous iterations' defs.
            var state = incoming
            var changed = true
            while (changed) {
                val before = state
                val after = walkOperation(operation.body, state, evaluator, defsOf)
                state = mergeDefs(before, after)
                changed = state != before
            }
            state
        }
        is AssignmentOperation -> {
            recordReads(operation.expression, incoming, evaluator, defsOf)
            val variable = evaluator.tryEvaluateTypedOrNull(VariableDeclaration::class.java, operation.reference)
                ?: return incoming
            // Kill previous defs of this variable; add this assignment.
            incoming + (variable to setOf(operation as Operation))
        }
        is HavocOperation -> {
            val variable = evaluator.tryEvaluateTypedOrNull(VariableDeclaration::class.java, operation.reference)
                ?: return incoming
            incoming + (variable to setOf(operation as Operation))
        }
        is AssumptionOperation -> {
            recordReads(operation.expression, incoming, evaluator, defsOf)
            incoming
        }
        is LocalVarDeclarationOperation -> {
            recordReads(operation.expression, incoming, evaluator, defsOf)
            incoming
        }
        is TraceOperation -> incoming
        else -> incoming
    }

    /** For every read expression in [expression], record its reaching definitions. */
    private fun recordReads(
        expression: Expression,
        incoming: Map<VariableDeclaration, Set<Operation>>,
        evaluator: MetaStaticExpressionEvaluator,
        defsOf: MutableMap<Expression, Set<Operation>>,
    ) {
        val candidates = sequenceOf(expression) + expression.eAllOfType<Expression>()
        for (candidate in candidates) {
            if (OxstsUtils.isWriteExpression(candidate)) continue
            val variable = evaluator.tryEvaluateTypedOrNull(VariableDeclaration::class.java, candidate) ?: continue
            defsOf[candidate] = incoming[variable].orEmpty()
        }
    }

    private fun mergeDefs(
        a: Map<VariableDeclaration, Set<Operation>>,
        b: Map<VariableDeclaration, Set<Operation>>,
    ): Map<VariableDeclaration, Set<Operation>> {
        if (a.isEmpty()) return b
        if (b.isEmpty()) return a
        val result = HashMap<VariableDeclaration, Set<Operation>>(a)
        for ((variable, defs) in b) {
            result[variable] = (result[variable].orEmpty()) + defs
        }
        return result
    }

    private fun variableOfWrite(
        operation: Operation,
        evaluator: MetaStaticExpressionEvaluator,
    ): VariableDeclaration = when (operation) {
        is AssignmentOperation -> evaluator.evaluateTyped(VariableDeclaration::class.java, operation.reference)
        is HavocOperation -> evaluator.evaluateTyped(VariableDeclaration::class.java, operation.reference)
        else -> error("Unexpected write operation: ${operation::class.simpleName}")
    }

    private fun transitionsOf(inlinedOxsts: InlinedOxsts): List<TransitionDeclaration> = listOfNotNull(
        inlinedOxsts.initTransition,
        inlinedOxsts.mainTransition,
    )

    private fun readsIn(
        inlinedOxsts: InlinedOxsts,
        evaluator: MetaStaticExpressionEvaluator,
    ): Sequence<Expression> = inlinedOxsts.eAllOfType<Expression>()
        .filterNot { OxstsUtils.isWriteExpression(it) }
        .filter { evaluator.tryEvaluateTypedOrNull(VariableDeclaration::class.java, it) != null }

}
