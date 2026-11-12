/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.verifier.witness

import com.google.inject.Inject
import hu.bme.mit.semantifyr.backend.transformation.BackendExpressionVisitor
import hu.bme.mit.semantifyr.backend.witness.InlinedWitness
import hu.bme.mit.semantifyr.backend.witness.WitnessState
import hu.bme.mit.semantifyr.compiler.pipeline.context.TransitionCallTraceMap
import hu.bme.mit.semantifyr.compiler.pipeline.expression.ExpressionEvaluationSerializer
import hu.bme.mit.semantifyr.compiler.pipeline.inlining.TransitionTracerNames
import hu.bme.mit.semantifyr.oxsts.lang.library.builtin.BuiltinSymbolResolver
import hu.bme.mit.semantifyr.oxsts.lang.naming.OxstsQualifiedNameProvider
import hu.bme.mit.semantifyr.oxsts.lang.serializer.ExpressionSerializer
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ArithmeticBinaryOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ArithmeticOp
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ArithmeticUnaryOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AssignmentOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AssumptionOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.BooleanOp
import hu.bme.mit.semantifyr.oxsts.model.oxsts.BooleanOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ChoiceOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ComparisonOp
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ComparisonOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.DataTypeDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ElementReference
import hu.bme.mit.semantifyr.oxsts.model.oxsts.EnumDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.EnumLiteral
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ForOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.HavocOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.IfOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.IfThenElse
import hu.bme.mit.semantifyr.oxsts.model.oxsts.IndexingSuffixExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralBoolean
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralInteger
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralReal
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LocalVarDeclarationOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.NegationOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Operation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.SequenceOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.TransitionDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.UnaryOp
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration

class ReplayStep(
    val index: Int,
    val exploredPaths: Int,
    val choicePoints: Int,
    val failure: String?,
)

class WitnessReplayResult(
    val steps: List<ReplayStep>,
) {
    val exploredPaths = steps.sumOf {
        it.exploredPaths
    }
    val firstFailure = steps.firstOrNull {
        it.failure != null
    }
}

class WitnessReplay @Inject constructor(
    private val builtinSymbolResolver: BuiltinSymbolResolver,
    private val expressionSerializer: ExpressionSerializer,
    private val expressionEvaluationSerializer: ExpressionEvaluationSerializer,
    private val oxstsQualifiedNameProvider: OxstsQualifiedNameProvider,
) {

    private class Continuation(
        val operation: Operation,
        val next: Continuation?,
    )

    private class Pruned(
        val depth: Int,
        val reason: String,
    )

    fun replay(witness: InlinedWitness, traces: TransitionCallTraceMap): WitnessReplayResult {
        val inlinedOxsts = witness.inlinedOxsts
        val states = listOf(witness.initializedState) + witness.transitionStates
        val initialValuation = declaredValuation(inlinedOxsts)
        val steps = mutableListOf<ReplayStep>()
        for ((index, state) in states.withIndex()) {
            val source = if (index == 0) {
                initialValuation
            } else {
                valuationOf(states[index - 1])
            }
            val transition = if (index == 0) {
                inlinedOxsts.initTransition
            } else {
                inlinedOxsts.mainTransition
            }
            val step = StepSearch(index, valuationOf(state), traces).run(transition, source)
            steps += step
            if (step.failure != null) {
                break
            }
        }
        return WitnessReplayResult(steps)
    }

    private fun declaredValuation(inlinedOxsts: InlinedOxsts): Map<VariableDeclaration, Any> {
        val valuation = mutableMapOf<VariableDeclaration, Any>()
        for (variable in inlinedOxsts.variables) {
            val initializer = variable.expression
            if (initializer != null) {
                valuation[variable] = Evaluator(valuation).evaluate(initializer)
            }
        }
        return valuation
    }

    private fun valuationOf(state: WitnessState): Map<VariableDeclaration, Any> {
        val evaluator = Evaluator(emptyMap())
        return state.values.associate {
            it.variable to evaluator.evaluate(it.value)
        }
    }

    private inner class StepSearch(
        private val index: Int,
        private val target: Map<VariableDeclaration, Any>,
        private val traces: TransitionCallTraceMap,
    ) {
        private val writtenAfter = mutableMapOf<Operation, Set<VariableDeclaration>>()
        private var exploredPaths = 0
        private var choicePoints = 0
        private var furthest: Pruned? = null

        fun run(transition: TransitionDeclaration, source: Map<VariableDeclaration, Any>): ReplayStep {
            for (branch in transition.branches) {
                collectWrittenAfter(branch, emptySet())
            }
            val accepted = transition.branches.any {
                search(Continuation(it, null), HashMap(source), 0)
            }
            val failure = if (accepted) {
                null
            } else {
                furthest?.reason ?: "no branch of the transition"
            }
            return ReplayStep(index, exploredPaths, choicePoints, failure)
        }

        private fun collectWrittenAfter(operation: Operation, after: Set<VariableDeclaration>): Set<VariableDeclaration> {
            return when (operation) {
                is SequenceOperation -> {
                    operation.steps.asReversed().fold(after) { written, step ->
                        collectWrittenAfter(step, written)
                    }
                }
                is ChoiceOperation -> {
                    operation.branches.fold(after) { written, branch ->
                        written + collectWrittenAfter(branch, after)
                    }
                }
                is IfOperation -> {
                    val elseBranch = operation.`else`
                    val written = collectWrittenAfter(operation.body, after)
                    if (elseBranch != null) {
                        written + collectWrittenAfter(elseBranch, after)
                    } else {
                        written
                    }
                }
                is AssignmentOperation -> {
                    writtenAfter[operation] = after
                    after + variableOf(operation.reference)
                }
                is HavocOperation -> {
                    writtenAfter[operation] = after
                    after + variableOf(operation.reference)
                }
                is ForOperation -> {
                    error("Runtime for is not replayed")
                }
                else -> {
                    after
                }
            }
        }

        private fun search(start: Continuation?, state: MutableMap<VariableDeclaration, Any>, startDepth: Int): Boolean {
            var current = start
            var depth = startDepth
            while (true) {
                if (current == null) {
                    return accept(state, depth)
                }
                val operation = current.operation
                current = current.next
                depth++
                when (operation) {
                    is SequenceOperation -> {
                        current = operation.steps.foldRight(current) { step, next ->
                            Continuation(step, next)
                        }
                    }
                    is ChoiceOperation -> {
                        if (operation.branches.size > 1) {
                            choicePoints++
                        }
                        val next = current
                        return operation.branches.any {
                            search(Continuation(it, next), HashMap(state), depth)
                        }
                    }
                    is IfOperation -> {
                        val guard = Evaluator(state).evaluate(operation.guard) as Boolean
                        val branch = if (guard) {
                            operation.body
                        } else {
                            operation.`else`
                        }
                        if (branch != null) {
                            current = Continuation(branch, current)
                        }
                    }
                    is AssumptionOperation -> {
                        if (!(Evaluator(state).evaluate(operation.expression) as Boolean)) {
                            return prune(depth, "the assumption ${expressionSerializer.serialize(operation.expression)} fails")
                        }
                    }
                    is AssignmentOperation -> {
                        val variable = variableOf(operation.reference)
                        val value = Evaluator(state).evaluate(operation.expression)
                        val expected = target[variable]
                        if (variable !in writtenAfter.getValue(operation) && expected != null && expected != value) {
                            return prune(depth, mismatch(variable, expected, value))
                        }
                        state[variable] = value
                    }
                    is HavocOperation -> {
                        val variable = variableOf(operation.reference)
                        val expected = target[variable]
                        if (variable !in writtenAfter.getValue(operation)) {
                            state[variable] = expected ?: error("The witness has no value for the havoced ${variable.name}")
                        } else {
                            val candidates = domainOf(variable) ?: error("The havoced ${variable.name} is written again and its domain cannot be enumerated")
                            choicePoints++
                            val next = current
                            return candidates.any {
                                val branchState = HashMap(state)
                                branchState[variable] = it
                                search(next, branchState, depth)
                            }
                        }
                    }
                    is LocalVarDeclarationOperation -> {
                        val initializer = operation.expression
                        if (initializer != null) {
                            state[operation] = Evaluator(state).evaluate(initializer)
                        }
                    }
                    else -> {
                        error("Unexpected operation in a flattened model: ${operation.eClass().name}")
                    }
                }
            }
        }

        private fun accept(state: Map<VariableDeclaration, Any>, depth: Int): Boolean {
            val differing = target.entries.filter {
                state[it.key] != it.value
            }
            if (differing.isEmpty()) {
                exploredPaths++
                return true
            }
            val reason = differing.joinToString("; ") {
                mismatch(it.key, it.value, state[it.key])
            }
            return prune(depth, "the path ends in a different state: $reason")
        }

        private fun prune(depth: Int, reason: String): Boolean {
            exploredPaths++
            val previous = furthest
            if (previous == null || depth > previous.depth) {
                furthest = Pruned(depth, reason)
            }
            return false
        }

        private fun mismatch(variable: VariableDeclaration, expected: Any, found: Any?): String {
            if (TransitionTracerNames.isTracerName(variable.name)) {
                val call = describeCall(variable)
                return if (expected == true) {
                    "the recorded call $call could not fire"
                } else {
                    "the unrecorded call $call had to fire"
                }
            }
            return "${variable.name} is ${format(found)} where the witness records ${format(expected)}"
        }

        private fun describeCall(tracer: VariableDeclaration): String {
            val trace = traces.getTransitionCallTrace(tracer)
            val self = expressionEvaluationSerializer.serialize(trace.self)
            val transition = oxstsQualifiedNameProvider.getFullyQualifiedNameString(trace.transitionDeclaration)
            return "$self.$transition (${tracer.name})"
        }
    }

    private fun format(value: Any?): String {
        return when (value) {
            null -> "unset"
            is EnumLiteral -> value.name
            else -> value.toString()
        }
    }

    private fun variableOf(reference: Expression): VariableDeclaration {
        return (reference as ElementReference).element as VariableDeclaration
    }

    private fun domainOf(variable: VariableDeclaration): List<Any>? {
        return when (val domain = variable.typeSpecification?.domain) {
            is EnumDeclaration -> domain.literals
            is DataTypeDeclaration -> {
                if (domain == builtinSymbolResolver.boolDatatype(domain)) {
                    listOf(
                        true,
                        false,
                    )
                } else {
                    null
                }
            }
            else -> null
        }
    }

    private class Evaluator(
        private val state: Map<VariableDeclaration, Any>,
    ) : BackendExpressionVisitor<Any>() {

        fun evaluate(expression: Expression): Any {
            return visit(expression)
        }

        override fun visit(expression: ComparisonOperator): Any {
            val left = visit(expression.left)
            val right = visit(expression.right)
            return when (expression.op) {
                ComparisonOp.EQ -> left == right
                ComparisonOp.NOT_EQ -> left != right
                ComparisonOp.LESS -> (left as Int) < (right as Int)
                ComparisonOp.LESS_EQ -> (left as Int) <= (right as Int)
                ComparisonOp.GREATER -> (left as Int) > (right as Int)
                ComparisonOp.GREATER_EQ -> (left as Int) >= (right as Int)
                null -> error("Comparison without an operator")
            }
        }

        override fun visit(expression: ArithmeticBinaryOperator): Any {
            val left = visit(expression.left) as Int
            val right = visit(expression.right) as Int
            return when (expression.op) {
                ArithmeticOp.ADD -> left + right
                ArithmeticOp.SUB -> left - right
                ArithmeticOp.MUL -> left * right
                ArithmeticOp.DIV -> left / right
                null -> error("Arithmetic without an operator")
            }
        }

        override fun visit(expression: BooleanOperator): Any {
            val left = visit(expression.left) as Boolean
            return when (expression.op) {
                BooleanOp.AND -> left && visit(expression.right) as Boolean
                BooleanOp.OR -> left || visit(expression.right) as Boolean
                BooleanOp.XOR -> left xor visit(expression.right) as Boolean
                null -> error("Boolean operator without an operator")
            }
        }

        override fun visit(expression: ArithmeticUnaryOperator): Any {
            val body = visit(expression.body) as Int
            return when (expression.op) {
                UnaryOp.PLUS -> body
                UnaryOp.MINUS -> -body
                null -> error("Unary operator without an operator")
            }
        }

        override fun visit(expression: NegationOperator): Any {
            return !(visit(expression.body) as Boolean)
        }

        override fun visit(expression: IfThenElse): Any {
            return if (visit(expression.guard) as Boolean) {
                visit(expression.then)
            } else {
                visit(expression.`else`)
            }
        }

        override fun visit(expression: LiteralReal): Any {
            error("Real literals are not replayed")
        }

        override fun visit(expression: LiteralInteger): Any {
            return expression.value
        }

        override fun visit(expression: LiteralBoolean): Any {
            return expression.isValue
        }

        override fun visit(expression: ElementReference): Any {
            return when (val element = expression.element) {
                is EnumLiteral -> element
                is VariableDeclaration -> state[element] ?: error("${element.name} is read before it has a value")
                else -> error("Unexpected element: ${element.eClass().name}")
            }
        }

        override fun visit(expression: IndexingSuffixExpression): Any {
            error("Indexing is not replayed")
        }
    }
}
