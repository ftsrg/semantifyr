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

data class ReachingDefinitionsInfo(
    val defsOf: Map<Expression, Set<EObject>>,
)

class ReachingDefinitionsAnalysis @Inject constructor(
    private val metaStaticExpressionEvaluatorProvider: MetaStaticExpressionEvaluatorProvider,
) : Analysis<ReachingDefinitionsInfo> {

    override fun compute(input: EvaluableCompilationContext): ReachingDefinitionsInfo {
        val evaluator = metaStaticExpressionEvaluatorProvider.getEvaluator(input.rootInstance)
        return ReachingDefinitionsComputation(input.inlinedOxsts, evaluator).compute()
    }

}

class ReachingDefinitionsComputation(
    private val inlinedOxsts: InlinedOxsts,
    private val evaluator: MetaStaticExpressionEvaluator,
) {

    private val defsOf = mutableMapOf<Expression, Set<EObject>>()

    fun compute(): ReachingDefinitionsInfo {
        val assignmentWrites: Sequence<EObject> = inlinedOxsts.eAllOfType<AssignmentOperation>().map {
            it as EObject
        } + inlinedOxsts.eAllOfType<HavocOperation>().map {
            it as EObject
        }

        val initialIn = assignmentWrites.groupBy {
            variableOfWrite(it as Operation)
        }.mapValues {
            it.value.toSet()
        }

        for (transition in transitionsOf(inlinedOxsts)) {
            for (branch in transition.branches) {
                walkOperation(branch, initialIn)
            }
        }

        val visitedReads = defsOf.keys.toSet()
        for (read in readsIn(inlinedOxsts)) {
            if (read in visitedReads) {
                continue
            }
            val variable = evaluator.tryEvaluateTypedOrNull(VariableDeclaration::class.java, read) ?: continue
            defsOf[read] = initialIn[variable].orEmpty()
        }

        return ReachingDefinitionsInfo(defsOf.toMap())
    }

    private fun walkOperation(
        operation: Operation,
        incoming: Map<VariableDeclaration, Set<EObject>>,
    ): Map<VariableDeclaration, Set<EObject>> {
        return when (operation) {
            is SequenceOperation -> {
                var state = incoming
                for (step in operation.steps) {
                    state = walkOperation(step, state)
                }
                state
            }
            is ChoiceOperation -> {
                operation.branches.map {
                    walkOperation(it, incoming)
                }.fold(emptyMap()) { acc, branchOut ->
                    mergeDefs(acc, branchOut)
                }
            }
            is IfOperation -> {
                recordReads(operation.guard, incoming)
                val bodyOut = walkOperation(operation.body, incoming)
                val elseOut = operation.`else`?.let {
                    walkOperation(it, incoming)
                } ?: incoming
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
                val variable = evaluator.tryEvaluateTypedOrNull(VariableDeclaration::class.java, operation.reference) ?: return incoming
                incoming + (variable to setOf<EObject>(operation))
            }
            is HavocOperation -> {
                val variable = evaluator.tryEvaluateTypedOrNull(VariableDeclaration::class.java, operation.reference) ?: return incoming
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
    }

    private fun recordReads(
        expression: Expression,
        incoming: Map<VariableDeclaration, Set<EObject>>,
    ) {
        val candidates = sequenceOf(expression) + expression.eAllOfType<Expression>()
        for (candidate in candidates) {
            if (OxstsUtils.isWriteExpression(candidate)) {
                continue
            }
            val variable = evaluator.tryEvaluateTypedOrNull(VariableDeclaration::class.java, candidate) ?: continue
            defsOf[candidate] = incoming[variable].orEmpty()
        }
    }

    private fun mergeDefs(
        a: Map<VariableDeclaration, Set<EObject>>,
        b: Map<VariableDeclaration, Set<EObject>>,
    ): Map<VariableDeclaration, Set<EObject>> {
        if (a.isEmpty()) {
            return b
        }
        if (b.isEmpty()) {
            return a
        }
        val result = LinkedHashMap<VariableDeclaration, Set<EObject>>(a)
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

    private fun transitionsOf(inlinedOxsts: InlinedOxsts): List<TransitionDeclaration> {
        return listOfNotNull(
            inlinedOxsts.initTransition,
            inlinedOxsts.mainTransition,
        )
    }

    private fun readsIn(
        inlinedOxsts: InlinedOxsts,
    ): Sequence<Expression> {
        return inlinedOxsts.eAllOfType<Expression>().filterNot {
            OxstsUtils.isWriteExpression(it)
        }.filter {
            evaluator.tryEvaluateTypedOrNull(VariableDeclaration::class.java, it) != null
        }
    }

}
