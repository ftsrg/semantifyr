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
import hu.bme.mit.semantifyr.compiler.pipeline.context.EvaluableCompilationContext
import hu.bme.mit.semantifyr.compiler.pipeline.expression.MetaStaticExpressionEvaluator
import hu.bme.mit.semantifyr.compiler.pipeline.expression.MetaStaticExpressionEvaluatorProvider
import hu.bme.mit.semantifyr.compiler.pipeline.expression.evaluateTyped
import hu.bme.mit.semantifyr.compiler.pipeline.expression.tryEvaluateTypedOrNull
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.Analysis
import hu.bme.mit.semantifyr.compiler.pipeline.utils.eAllOfType
import org.eclipse.emf.ecore.EObject
import kotlin.collections.iterator

/**
 * Result of reaching-definitions analysis: for each variable read expression,
 * the set of definitions that could have produced its value at that program
 * point.
 *
 * A definition is one of:
 *  - an [AssignmentOperation] whose target is the variable,
 *  - a [HavocOperation] on the variable, or
 *  - a [LocalVarDeclarationOperation] when the read is within the local's
 *    lexical scope and no later write in the same iteration has overwritten
 *    it yet. A local variable is re-initialized every time its declaration
 *    runs, so the declaration is a fresh def at that program point.
 *
 * Class-level / inlinedOxsts-level [VariableDeclaration]s are NOT reaching
 * defs: after the init transition runs the property only sees post-init
 * state, and the declared initializer value is only observable if the
 * init transition preserves it (captured by init having no write to the
 * variable, which [hu.bme.mit.semantifyr.compiler.pipeline.optimization.passes.VariableLivenessPass]'s
 * init-only substitution handles separately).
 *
 * A single-element set means the value is uniquely determined by that
 * definition, enabling copy propagation.
 */
data class ReachingDefinitionsInfo(
    val defsOf: Map<Expression, Set<EObject>>,
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

    override fun compute(input: EvaluableCompilationContext): ReachingDefinitionsInfo {
        val evaluator = metaStaticExpressionEvaluatorProvider.getEvaluator(input.rootInstance)
        return ReachingDefinitionsComputation(input.inlinedOxsts, evaluator).compute()
    }

}

/**
 * Stateless per-compute engine for reaching-definitions analysis. Holds no
 * mutable public state - [compute] returns the full result, and the
 * intermediate [defsOf] is scoped to this instance. Factored out of
 * [ReachingDefinitionsAnalysis] so the algorithm can be exercised directly
 * without the DI-bound analysis wrapper.
 */
class ReachingDefinitionsComputation(
    private val inlinedOxsts: InlinedOxsts,
    private val evaluator: MetaStaticExpressionEvaluator,
) {

    private val defsOf = mutableMapOf<Expression, Set<EObject>>()

    fun compute(): ReachingDefinitionsInfo {
        // Initial definitions: approximate the transition-entry state as
        // "every write in the IR could have produced the current value".
        // This is conservative but safe.
        //
        // Class-level variable declarations are NOT added here: after init
        // runs, the declaration's initializer value is only observable to
        // the property if init leaves the variable untouched, and that case
        // is handled by VariableLivenessPass (init-only substitution) rather
        // than here.
        //
        // Local variable declarations are also not added here: they are not
        // visible at transition entry (they do not exist yet), and the
        // walker seeds them at their LocalVarDeclarationOperation site.
        val assignmentWrites: Sequence<EObject> =
            inlinedOxsts.eAllOfType<AssignmentOperation>().map { it as EObject } +
                inlinedOxsts.eAllOfType<HavocOperation>().map { it as EObject }
        val initialIn: Map<VariableDeclaration, Set<EObject>> = assignmentWrites
            .groupBy { variableOfWrite(it as Operation) }
            .mapValues { it.value.toSet() }

        // Analyse each transition independently - assignments between transitions
        // are joined via the conservative initialIn.
        for (transition in transitionsOf(inlinedOxsts)) {
            for (branch in transition.branches) {
                walkOperation(branch, initialIn)
            }
        }

        // Reads outside any transition (e.g., property expression, variable
        // initializers) see the full set of writes as reaching them.
        val visitedReads = defsOf.keys.toSet()
        for (read in readsIn(inlinedOxsts)) {
            if (read in visitedReads) continue
            val variable = evaluator.tryEvaluateTypedOrNull(VariableDeclaration::class.java, read) ?: continue
            defsOf[read] = initialIn[variable].orEmpty()
        }

        return ReachingDefinitionsInfo(defsOf.toMap())
    }

    /**
     * Walks [operation] with incoming reaching definitions [incoming].
     * Populates [defsOf] for each read encountered.
     * Returns the outgoing reaching definitions after [operation] executes.
     */
    private fun walkOperation(
        operation: Operation,
        incoming: Map<VariableDeclaration, Set<EObject>>,
    ): Map<VariableDeclaration, Set<EObject>> = when (operation) {
        is SequenceOperation -> {
            var state = incoming
            for (step in operation.steps) {
                state = walkOperation(step, state)
            }
            state
        }
        is ChoiceOperation -> {
            operation.branches
                .map { walkOperation(it, incoming) }
                .fold(emptyMap<VariableDeclaration, Set<EObject>>()) { acc, branchOut -> mergeDefs(acc, branchOut) }
        }
        is IfOperation -> {
            recordReads(operation.guard, incoming)
            val bodyOut = walkOperation(operation.body, incoming)
            val elseOut = operation.`else`?.let { walkOperation(it, incoming) } ?: incoming
            mergeDefs(bodyOut, elseOut)
        }
        is ForOperation -> {
            recordReads(operation.rangeExpression, incoming)
            var state = incoming
            var changed = true
            while (changed) {
                val before = state
                val after = walkOperation(operation.body, state)
                state = mergeDefs(before, after)
                changed = state != before
            }
            state
        }
        is AssignmentOperation -> {
            recordReads(operation.expression, incoming)
            val variable = evaluator.tryEvaluateTypedOrNull(VariableDeclaration::class.java, operation.reference)
                ?: return incoming
            incoming + (variable to setOf<EObject>(operation))
        }
        is HavocOperation -> {
            val variable = evaluator.tryEvaluateTypedOrNull(VariableDeclaration::class.java, operation.reference)
                ?: return incoming
            incoming + (variable to setOf<EObject>(operation))
        }
        is AssumptionOperation -> {
            recordReads(operation.expression, incoming)
            incoming
        }
        is LocalVarDeclarationOperation -> {
            recordReads(operation.expression, incoming)
            incoming + (operation to setOf<EObject>(operation))
        }
        is TraceOperation -> incoming
        else -> incoming
    }

    private fun recordReads(
        expression: Expression,
        incoming: Map<VariableDeclaration, Set<EObject>>,
    ) {
        val candidates = sequenceOf(expression) + expression.eAllOfType<Expression>()
        for (candidate in candidates) {
            if (OxstsUtils.isWriteExpression(candidate)) continue
            val variable = evaluator.tryEvaluateTypedOrNull(VariableDeclaration::class.java, candidate) ?: continue
            defsOf[candidate] = incoming[variable].orEmpty()
        }
    }

    private fun mergeDefs(
        a: Map<VariableDeclaration, Set<EObject>>,
        b: Map<VariableDeclaration, Set<EObject>>,
    ): Map<VariableDeclaration, Set<EObject>> {
        if (a.isEmpty()) return b
        if (b.isEmpty()) return a
        val result = HashMap<VariableDeclaration, Set<EObject>>(a)
        for ((variable, defs) in b) {
            result[variable] = (result[variable].orEmpty()) + defs
        }
        return result
    }

    private fun variableOfWrite(
        operation: Operation,
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
    ): Sequence<Expression> = inlinedOxsts.eAllOfType<Expression>()
        .filterNot { OxstsUtils.isWriteExpression(it) }
        .filter { evaluator.tryEvaluateTypedOrNull(VariableDeclaration::class.java, it) != null }

}
