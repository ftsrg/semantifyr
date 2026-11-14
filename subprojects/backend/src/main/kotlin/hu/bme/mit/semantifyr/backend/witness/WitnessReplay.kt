/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backend.witness

import com.google.inject.Inject
import hu.bme.mit.semantifyr.backend.transformation.BackendExpressionVisitor
import hu.bme.mit.semantifyr.compiler.pipeline.context.TransitionCallTraceMap
import hu.bme.mit.semantifyr.compiler.pipeline.expression.ExpressionEvaluationSerializer
import hu.bme.mit.semantifyr.compiler.pipeline.inlining.TransitionTracerNames
import hu.bme.mit.semantifyr.oxsts.lang.library.builtin.BuiltinSymbolResolver
import hu.bme.mit.semantifyr.oxsts.lang.naming.OxstsQualifiedNameProvider
import hu.bme.mit.semantifyr.oxsts.lang.serializer.ExpressionSerializer
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ArithmeticBinaryOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ArithmeticOp
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ArithmeticUnaryOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ArrayLiteral
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
import hu.bme.mit.semantifyr.oxsts.model.oxsts.RangeExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.SequenceOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.TransitionDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.UnaryOp
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration
import org.eclipse.emf.ecore.EObject

enum class ReplayOutcome {
    Accepted,
    Rejected,
    Undecided,
}

class ReplayStep(
    val index: Int,
    val outcome: ReplayOutcome,
    val exploredPaths: Int,
    val choicePoints: Int,
    val reason: String?,
)

class WitnessReplayResult(
    val steps: List<ReplayStep>,
    val exercised: Set<String>,
) {
    val exploredPaths = steps.sumOf {
        it.exploredPaths
    }
    val mostPathsInStep = steps.maxOfOrNull {
        it.exploredPaths
    } ?: 0
    val firstFailure = steps.firstOrNull {
        it.outcome != ReplayOutcome.Accepted
    }
    val outcome = firstFailure?.outcome ?: ReplayOutcome.Accepted
}

private const val LENIENT_PATH_LIMIT = 100_000

class WitnessReplay @Inject constructor(
    private val builtinSymbolResolver: BuiltinSymbolResolver,
    private val expressionSerializer: ExpressionSerializer,
    private val expressionEvaluationSerializer: ExpressionEvaluationSerializer,
    private val oxstsQualifiedNameProvider: OxstsQualifiedNameProvider,
) {

    private sealed interface Work

    private class Execute(
        val operation: Operation,
    ) : Work

    private class Bind(
        val variable: VariableDeclaration,
        val value: Any,
    ) : Work

    private class Continuation(
        val work: Work,
        val next: Continuation?,
    )

    private class Pruned(
        val depth: Int,
        val reason: String,
    )

    private class Undecided(
        val reason: String,
    ) : Exception(reason)

    fun replay(witness: InlinedWitness, traces: TransitionCallTraceMap?): WitnessReplayResult {
        val inlinedOxsts = witness.inlinedOxsts
        val states = listOf(witness.initializedState) + witness.transitionStates
        val exercised = mutableSetOf<String>()
        val initialValuation = declaredValuation(inlinedOxsts, exercised)
        val steps = mutableListOf<ReplayStep>()
        for ((index, state) in states.withIndex()) {
            val source = if (index == 0) {
                initialValuation
            } else {
                valuationOf(states[index - 1], exercised)
            }
            val transition = if (index == 0) {
                inlinedOxsts.initTransition
            } else {
                inlinedOxsts.mainTransition
            }
            val target = valuationOf(state, exercised)
            val step = StepSearch(index, target, traces, exercised, false).run(transition, source)
            if (step.outcome == ReplayOutcome.Rejected) {
                steps += StepSearch(index, target, traces, exercised, true).diagnose(transition, source, step)
                break
            }
            steps += step
            if (step.outcome != ReplayOutcome.Accepted) {
                break
            }
        }
        return WitnessReplayResult(steps, exercised)
    }

    private fun declaredValuation(inlinedOxsts: InlinedOxsts, exercised: MutableSet<String>): Map<VariableDeclaration, Any> {
        val valuation = mutableMapOf<VariableDeclaration, Any>()
        for (variable in inlinedOxsts.variables) {
            val initializer = variable.expression
            if (initializer != null) {
                valuation[variable] = Evaluator(valuation, exercised).evaluate(initializer)
            }
        }
        return valuation
    }

    private fun valuationOf(state: WitnessState, exercised: MutableSet<String>): Map<VariableDeclaration, Any> {
        val evaluator = Evaluator(emptyMap(), exercised)
        return state.values.associate {
            it.variable to evaluator.evaluate(it.value)
        }
    }

    private inner class StepSearch(
        private val index: Int,
        private val target: Map<VariableDeclaration, Any>,
        private val traces: TransitionCallTraceMap?,
        private val exercised: MutableSet<String>,
        private val lenient: Boolean,
    ) {
        private val writtenAfter = mutableMapOf<Operation, Set<VariableDeclaration>>()
        private var exploredPaths = 0
        private var choicePoints = 0
        private var furthest: Pruned? = null
        private var furthestTracer: Pruned? = null
        private var undecided: String? = null
        private var closest: List<VariableDeclaration>? = null
        private var closestState: Map<VariableDeclaration, Any>? = null

        fun run(transition: TransitionDeclaration, source: Map<VariableDeclaration, Any>): ReplayStep {
            for (branch in transition.branches) {
                collectWrittenAfter(branch, emptySet())
            }
            val accepted = transition.branches.any {
                search(Continuation(Execute(it), null), HashMap(source), 0)
            }
            val undecidedReason = undecided
            return when {
                accepted -> ReplayStep(index, ReplayOutcome.Accepted, exploredPaths, choicePoints, null)
                undecidedReason != null -> ReplayStep(index, ReplayOutcome.Undecided, exploredPaths, choicePoints, undecidedReason)
                else -> {
                    val reason = furthestTracer?.reason ?: furthest?.reason ?: "no branch of the transition"
                    ReplayStep(index, ReplayOutcome.Rejected, exploredPaths, choicePoints, reason)
                }
            }
        }

        // without the early checks every path is run to its end, and the one ending closest to the recorded state names the fault
        fun diagnose(transition: TransitionDeclaration, source: Map<VariableDeclaration, Any>, rejection: ReplayStep): ReplayStep {
            run(transition, source)
            val differing = closest ?: return rejection
            val state = checkNotNull(closestState)
            val ordered = differing.sortedByDescending {
                isTracer(it)
            }
            val reason = ordered.joinToString("; ") {
                mismatch(it, target.getValue(it), state[it])
            }
            return ReplayStep(index, ReplayOutcome.Rejected, rejection.exploredPaths, rejection.choicePoints, "the closest of $exploredPaths paths differs in ${differing.size}: $reason")
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
                is ForOperation -> {
                    // a later iteration writes what the body writes, so the body is analysed with its own writes after it
                    val bodyWrites = collectWrittenAfter(operation.body, after)
                    collectWrittenAfter(operation.body, bodyWrites) + operation.loopVariable
                }
                is AssignmentOperation -> {
                    writtenAfter[operation] = after
                    after + variableOf(operation.reference)
                }
                is HavocOperation -> {
                    writtenAfter[operation] = after
                    after + variableOf(operation.reference)
                }
                else -> {
                    after
                }
            }
        }

        private fun search(start: Continuation?, state: MutableMap<VariableDeclaration, Any>, startDepth: Int): Boolean {
            try {
                return searchOrThrow(start, state, startDepth)
            } catch (exception: Undecided) {
                exploredPaths++
                undecided = exception.reason
                return false
            }
        }

        private fun searchOrThrow(start: Continuation?, state: MutableMap<VariableDeclaration, Any>, startDepth: Int): Boolean {
            var current = start
            var depth = startDepth
            while (true) {
                if (current == null) {
                    return accept(state, depth)
                }
                val work = current.work
                current = current.next
                depth++
                if (work is Bind) {
                    state[work.variable] = work.value
                    continue
                }
                val operation = (work as Execute).operation
                exercised += operation.eClass().name
                val evaluator = Evaluator(state, exercised)
                when (operation) {
                    is SequenceOperation -> {
                        current = operation.steps.foldRight(current) { step, next ->
                            Continuation(Execute(step), next)
                        }
                    }
                    is ChoiceOperation -> {
                        if (operation.branches.size > 1) {
                            choicePoints++
                        }
                        val next = current
                        return operation.branches.any {
                            search(Continuation(Execute(it), next), HashMap(state), depth)
                        }
                    }
                    is IfOperation -> {
                        val branch = if (evaluator.evaluate(operation.guard) as Boolean) {
                            operation.body
                        } else {
                            operation.`else`
                        }
                        if (branch != null) {
                            current = Continuation(Execute(branch), current)
                        }
                    }
                    is ForOperation -> {
                        val range = operation.rangeExpression as RangeExpression
                        val from = evaluator.evaluate(range.left) as Int
                        val to = evaluator.evaluate(range.right) as Int
                        val last = if (range.isExclusive) {
                            to - 1
                        } else {
                            to
                        }
                        for (iteration in last downTo from) {
                            current = Continuation(Bind(operation.loopVariable, iteration), Continuation(Execute(operation.body), current))
                        }
                    }
                    is AssumptionOperation -> {
                        if (!(evaluator.evaluate(operation.expression) as Boolean)) {
                            return prune(depth, "the assumption ${expressionSerializer.serialize(operation.expression)} fails", false)
                        }
                    }
                    is AssignmentOperation -> {
                        val variable = variableOf(operation.reference)
                        val value = assigned(state, operation.reference, evaluator.evaluate(operation.expression), evaluator)
                        val expected = target[variable]
                        if (!lenient && variable !in writtenAfter.getValue(operation) && expected != null && expected != value) {
                            return prune(depth, mismatch(variable, expected, value), isTracer(variable))
                        }
                        state[variable] = value
                    }
                    is HavocOperation -> {
                        val variable = variableOf(operation.reference)
                        val expected = target[variable]
                        if (operation.reference !is ElementReference) {
                            throw Undecided("the havoc of an element of ${variable.name} is not replayed")
                        }
                        if (variable !in writtenAfter.getValue(operation) && expected != null) {
                            state[variable] = expected
                        } else {
                            val candidates = domainOf(variable) ?: if (expected == null) {
                                throw Undecided("the witness holds no value for the havoced ${variable.name}")
                            } else {
                                throw Undecided("the havoced integer ${variable.name} is written again in the step, so the witness does not hold the havoced value")
                            }
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
                            state[operation] = evaluator.evaluate(initializer)
                        }
                    }
                    else -> {
                        error("Unexpected operation in a flattened model: ${operation.eClass().name}")
                    }
                }
            }
        }

        private fun assigned(state: Map<VariableDeclaration, Any>, reference: Expression, value: Any, evaluator: Evaluator): Any {
            if (reference !is IndexingSuffixExpression) {
                return value
            }
            val variable = variableOf(reference)
            val array = state[variable] as? List<*> ?: error("${variable.name} is written by index before it has a value")
            val index = evaluator.evaluate(reference.index) as Int
            return array.toMutableList().also {
                it[index] = value
            }
        }

        private fun accept(state: Map<VariableDeclaration, Any>, depth: Int): Boolean {
            val differing = target.entries.filter {
                state[it.key] != it.value
            }
            if (lenient) {
                exploredPaths++
                val previous = closest
                if (previous == null || differing.size < previous.size) {
                    closest = differing.map {
                        it.key
                    }
                    closestState = HashMap(state)
                }
                return exploredPaths >= LENIENT_PATH_LIMIT
            }
            if (differing.isEmpty()) {
                exploredPaths++
                return true
            }
            val reason = differing.joinToString("; ") {
                mismatch(it.key, it.value, state[it.key])
            }
            val tracer = differing.any {
                isTracer(it.key)
            }
            return prune(depth, "the path ends in a different state: $reason", tracer)
        }

        private fun prune(depth: Int, reason: String, tracer: Boolean): Boolean {
            exploredPaths++
            val previous = furthest
            if (previous == null || depth > previous.depth) {
                furthest = Pruned(depth, reason)
            }
            val previousTracer = furthestTracer
            if (tracer && (previousTracer == null || depth > previousTracer.depth)) {
                furthestTracer = Pruned(depth, reason)
            }
            return false
        }

        private fun isTracer(variable: VariableDeclaration): Boolean {
            return TransitionTracerNames.isTracerName(variable.name)
        }

        private fun mismatch(variable: VariableDeclaration, expected: Any, found: Any?): String {
            if (isTracer(variable)) {
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
            val trace = traces?.getTransitionCallTrace(tracer) ?: return tracer.name
            val self = expressionEvaluationSerializer.serialize(trace.self)
            val transition = oxstsQualifiedNameProvider.getFullyQualifiedNameString(trace.transitionDeclaration)
            return "$self.$transition (${tracer.name})"
        }
    }

    private fun format(value: Any?): String {
        return when (value) {
            null -> "unset"
            is EnumLiteral -> value.name
            is List<*> -> value.joinToString(", ", "[", "]") {
                format(it)
            }
            else -> value.toString()
        }
    }

    private fun variableOf(reference: Expression): VariableDeclaration {
        return when (reference) {
            is ElementReference -> reference.element as VariableDeclaration
            is IndexingSuffixExpression -> variableOf(reference.primary)
            else -> error("Unexpected reference: ${reference.eClass().name}")
        }
    }

    private fun domainOf(variable: VariableDeclaration): List<Any>? {
        if (variable.typeSpecification?.multiplicity != null) {
            return null
        }
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
        private val exercised: MutableSet<String>,
    ) : BackendExpressionVisitor<Any>() {

        fun evaluate(expression: Expression): Any {
            return visit(expression)
        }

        private fun note(element: EObject) {
            exercised += element.eClass().name
        }

        override fun visit(expression: ComparisonOperator): Any {
            note(expression)
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
            note(expression)
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
            note(expression)
            val left = visit(expression.left) as Boolean
            return when (expression.op) {
                BooleanOp.AND -> left && visit(expression.right) as Boolean
                BooleanOp.OR -> left || visit(expression.right) as Boolean
                BooleanOp.XOR -> left xor visit(expression.right) as Boolean
                null -> error("Boolean operator without an operator")
            }
        }

        override fun visit(expression: ArithmeticUnaryOperator): Any {
            note(expression)
            val body = visit(expression.body) as Int
            return when (expression.op) {
                UnaryOp.PLUS -> body
                UnaryOp.MINUS -> -body
                null -> error("Unary operator without an operator")
            }
        }

        override fun visit(expression: NegationOperator): Any {
            note(expression)
            return !(visit(expression.body) as Boolean)
        }

        override fun visit(expression: IfThenElse): Any {
            note(expression)
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
            note(expression)
            return expression.value
        }

        override fun visit(expression: LiteralBoolean): Any {
            note(expression)
            return expression.isValue
        }

        override fun visit(expression: ArrayLiteral): Any {
            note(expression)
            return expression.values.map {
                visit(it)
            }
        }

        override fun visit(expression: ElementReference): Any {
            note(expression)
            return when (val element = expression.element) {
                is EnumLiteral -> element
                is VariableDeclaration -> state[element] ?: error("${element.name} is read before it has a value")
                else -> error("Unexpected element: ${element.eClass().name}")
            }
        }

        override fun visit(expression: IndexingSuffixExpression): Any {
            note(expression)
            val array = visit(expression.primary) as List<*>
            val index = visit(expression.index) as Int
            return array[index] ?: error("Index $index has no value")
        }
    }
}
