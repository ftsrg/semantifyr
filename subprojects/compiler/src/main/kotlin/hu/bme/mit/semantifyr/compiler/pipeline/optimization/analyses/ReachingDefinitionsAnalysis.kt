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
import hu.bme.mit.semantifyr.compiler.pipeline.expression.tryEvaluateTypedOrNull
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.Analysis
import hu.bme.mit.semantifyr.compiler.pipeline.utils.initMustWrite
import hu.bme.mit.semantifyr.compiler.pipeline.utils.variableReadExpressions
import hu.bme.mit.semantifyr.compiler.pipeline.utils.variableWrites
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
import hu.bme.mit.semantifyr.oxsts.model.oxsts.TransitionDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration
import org.eclipse.emf.ecore.EObject

data class ReachingDefinitionsInfo(
    val definitionsOf: Map<Expression, Set<EObject>>,
    val exitReaching: Set<EObject>,
)

class ReachingDefinitionsAnalysis @Inject constructor(
    private val metaCompileTimeExpressionEvaluatorProvider: MetaCompileTimeExpressionEvaluatorProvider,
) : Analysis<ReachingDefinitionsInfo> {

    override fun compute(input: EvaluableCompilationContext): ReachingDefinitionsInfo {
        val evaluator = metaCompileTimeExpressionEvaluatorProvider.getEvaluator(input.rootInstance)
        return ReachingDefinitionsComputation(input.inlinedOxsts, evaluator).compute()
    }

}

class ReachingDefinitionsComputation(
    private val inlinedOxsts: InlinedOxsts,
    private val evaluator: MetaCompileTimeExpressionEvaluator,
) {

    private val definitionsOf = mutableMapOf<Expression, Set<EObject>>()
    private val exitReaching = mutableSetOf<EObject>()

    private val topLevelVariables by lazy {
        inlinedOxsts.variables.toSet()
    }

    private val readVariables by lazy {
        inlinedOxsts.variableReadExpressions(evaluator).mapNotNull {
            evaluateVariable(it)
        }.toSet()
    }

    fun compute(): ReachingDefinitionsInfo {
        val initialIn = buildInitialIn()
        val declarations = inlinedOxsts.variables.associateWith {
            setOf<EObject>(it)
        }
        walkTransition(inlinedOxsts.initTransition, declarations)
        walkTransition(inlinedOxsts.mainTransition, initialIn)
        recordFallbackReads(initialIn)

        return ReachingDefinitionsInfo(definitionsOf.toMap(), exitReaching.toSet())
    }

    private fun buildInitialIn(): Map<VariableDeclaration, Set<EObject>> {
        val writesByVariable = inlinedOxsts.variableWrites(evaluator).mapValues {
            it.value.toSet()
        }

        val initial = LinkedHashMap<VariableDeclaration, Set<EObject>>()
        for ((variable, defs) in writesByVariable) {
            initial[variable] = if (inlinedOxsts.initMustWrite(variable, evaluator)) {
                defs
            } else {
                defs + variable
            }
        }
        for (variable in inlinedOxsts.variables) {
            initial.getOrPut(variable) { setOf(variable) }
        }
        return initial
    }

    private fun walkTransition(
        transition: TransitionDeclaration,
        startState: Map<VariableDeclaration, Set<EObject>>,
    ) {
        for (branch in transition.branches) {
            harvestExit(walkOperation(branch, startState))
        }
    }

    private fun harvestExit(state: Map<VariableDeclaration, Set<EObject>>) {
        for ((variable, defs) in state) {
            if (variable !in topLevelVariables) {
                continue
            }
            if (variable !in readVariables) {
                continue
            }
            for (def in defs) {
                if (def is AssignmentOperation || def is HavocOperation) {
                    exitReaching += def
                }
            }
        }
    }

    private fun recordFallbackReads(initialIn: Map<VariableDeclaration, Set<EObject>>) {
        for (read in inlinedOxsts.variableReadExpressions(evaluator)) {
            if (read in definitionsOf) {
                continue
            }
            val variable = evaluateVariable(read) ?: continue
            // the property is checked in the pre-init state too, where the declaration holds
            definitionsOf[read] = initialIn[variable].orEmpty() + variable
        }
    }

    private fun walkOperation(
        operation: Operation,
        incoming: Map<VariableDeclaration, Set<EObject>>,
    ): Map<VariableDeclaration, Set<EObject>> {
        return when (operation) {
            is SequenceOperation -> walkSequence(operation, incoming)
            is ChoiceOperation -> walkChoice(operation, incoming)
            is IfOperation -> walkIf(operation, incoming)
            is ForOperation -> walkFor(operation, incoming)
            is AssignmentOperation -> {
                recordReads(operation.expression, incoming)
                val variable = evaluateVariable(operation.reference) ?: return incoming
                incoming + (variable to setOf<EObject>(operation))
            }
            is HavocOperation -> {
                val variable = evaluateVariable(operation.reference) ?: return incoming
                incoming + (variable to setOf<EObject>(operation))
            }
            is AssumptionOperation -> {
                recordReads(operation.expression, incoming)
                incoming
            }
            is LocalVarDeclarationOperation -> {
                operation.expression?.let {
                    recordReads(it, incoming)
                }
                incoming + (operation to setOf<EObject>(operation))
            }
            else -> incoming
        }
    }

    private fun walkSequence(
        operation: SequenceOperation,
        incoming: Map<VariableDeclaration, Set<EObject>>,
    ): Map<VariableDeclaration, Set<EObject>> {
        var state = incoming
        for (step in operation.steps) {
            state = walkOperation(step, state)
        }
        return state
    }

    private fun walkChoice(
        operation: ChoiceOperation,
        incoming: Map<VariableDeclaration, Set<EObject>>,
    ): Map<VariableDeclaration, Set<EObject>> {
        return operation.branches.fold(emptyMap()) { acc, branch ->
            mergeDefs(acc, walkOperation(branch, incoming))
        }
    }

    private fun walkIf(
        operation: IfOperation,
        incoming: Map<VariableDeclaration, Set<EObject>>,
    ): Map<VariableDeclaration, Set<EObject>> {
        recordReads(operation.guard, incoming)
        val bodyOut = walkOperation(operation.body, incoming)
        val elseOut = operation.`else`?.let {
            walkOperation(it, incoming)
        } ?: incoming
        return mergeDefs(bodyOut, elseOut)
    }

    private fun walkFor(
        operation: ForOperation,
        incoming: Map<VariableDeclaration, Set<EObject>>,
    ): Map<VariableDeclaration, Set<EObject>> {
        recordReads(operation.rangeExpression, incoming)
        var state = incoming
        while (true) {
            val merged = mergeDefs(state, walkOperation(operation.body, state))
            if (merged == state) {
                break
            }
            state = merged
        }
        return state
    }

    private fun recordReads(
        expression: Expression,
        incoming: Map<VariableDeclaration, Set<EObject>>,
    ) {
        for (read in expression.variableReadExpressions(evaluator)) {
            val variable = evaluateVariable(read)
            definitionsOf[read] = incoming[variable].orEmpty()
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
            result[variable] = result[variable].orEmpty() + defs
        }
        return result
    }

    private fun evaluateVariable(expression: Expression): VariableDeclaration? {
        return evaluator.tryEvaluateTypedOrNull(VariableDeclaration::class.java, expression)
    }

}
