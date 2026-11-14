/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backend.witness

import com.google.inject.Inject
import hu.bme.mit.semantifyr.backend.transformation.BackendExpressionVisitor
import hu.bme.mit.semantifyr.compiler.pipeline.inlining.TransitionTracerNames
import hu.bme.mit.semantifyr.oxsts.lang.library.builtin.BuiltinSymbolResolver
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

data class EnumValue(
    val name: String,
)

class InterpretedPath(
    val calls: List<VariableDeclaration>,
    val decisions: List<Pair<Operation, Int>>,
    val endState: Map<VariableDeclaration, Any>,
)

class InterpretedStep(
    val index: Int,
    val sources: Int,
    val exploredPaths: Int,
    val fittingPaths: List<InterpretedPath>,
    val undecided: String?,
) {
    val distinctSequences = fittingPaths.map {
        it.calls
    }.distinct()
    val distinctSets = fittingPaths.map {
        it.calls.toSet()
    }.distinct()
    val nextStates = fittingPaths.map { path ->
        path.endState.filterKeys {
            !TransitionTracerNames.isTracerName(it.name)
        }
    }.distinct()
}

private const val PATH_LIMIT = 1_000_000

// executes every path of a step from the states carried forward and keeps those ending in the recorded state; no trace value guides it
class StepInterpreter @Inject constructor(
    private val builtinSymbolResolver: BuiltinSymbolResolver,
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

    private class Undecided(
        reason: String,
    ) : Exception(reason)

    val exercised = mutableSetOf<String>()

    fun interpret(model: InlinedOxsts, recorded: List<Map<VariableDeclaration, Any>>): List<InterpretedStep> {
        val globals = model.variables.toSet()
        var frontier = listOf(declaredValuation(model))
        val steps = mutableListOf<InterpretedStep>()
        for ((index, target) in recorded.withIndex()) {
            val transition = if (index == 0) {
                model.initTransition
            } else {
                model.mainTransition
            }
            val search = StepSearch(target, globals)
            for (source in frontier) {
                search.run(transition, source)
            }
            val step = InterpretedStep(index, frontier.size, search.exploredPaths, search.fitting, search.undecided)
            steps += step
            if (step.fittingPaths.isEmpty()) {
                break
            }
            frontier = step.nextStates
        }
        return steps
    }

    private fun declaredValuation(model: InlinedOxsts): Map<VariableDeclaration, Any> {
        val valuation = mutableMapOf<VariableDeclaration, Any>()
        for (variable in model.variables) {
            val initializer = variable.expression
            if (initializer != null) {
                valuation[variable] = Evaluator(valuation).evaluate(initializer)
            }
        }
        return valuation
    }

    fun evaluateConstant(expression: Expression): Any {
        return Evaluator(emptyMap()).evaluate(expression)
    }

    private fun isTracer(variable: VariableDeclaration): Boolean {
        return TransitionTracerNames.isTracerName(variable.name)
    }

    private inner class StepSearch(
        private val target: Map<VariableDeclaration, Any>,
        private val globals: Set<VariableDeclaration>,
    ) {
        private val writtenAfter = mutableMapOf<Operation, Set<VariableDeclaration>>()
        private val analysed = mutableSetOf<TransitionDeclaration>()
        var exploredPaths = 0
        val fitting = mutableListOf<InterpretedPath>()
        var undecided: String? = null

        fun run(transition: TransitionDeclaration, source: Map<VariableDeclaration, Any>) {
            if (analysed.add(transition)) {
                for (branch in transition.branches) {
                    collectWrittenAfter(branch, emptySet())
                }
            }
            for (branch in transition.branches) {
                search(Continuation(Execute(branch), null), HashMap(source), emptyList(), emptyList())
            }
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
                        written + after
                    }
                }
                is ForOperation -> {
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

        private fun search(start: Continuation?, state: MutableMap<VariableDeclaration, Any>, startCalls: List<VariableDeclaration>, decisions: List<Pair<Operation, Int>>) {
            try {
                searchOrThrow(start, state, startCalls, decisions)
            } catch (exception: Undecided) {
                exploredPaths++
                undecided = exception.message
            }
        }

        private fun searchOrThrow(start: Continuation?, state: MutableMap<VariableDeclaration, Any>, startCalls: List<VariableDeclaration>, decisions: List<Pair<Operation, Int>>) {
            var current = start
            var calls = startCalls
            while (true) {
                if (current == null) {
                    accept(state, calls, decisions)
                    return
                }
                val work = current.work
                current = current.next
                if (work is Bind) {
                    state[work.variable] = work.value
                    continue
                }
                val operation = (work as Execute).operation
                exercised += operation.eClass().name
                val evaluator = Evaluator(state)
                when (operation) {
                    is SequenceOperation -> {
                        current = operation.steps.foldRight(current) { step, next ->
                            Continuation(Execute(step), next)
                        }
                    }
                    is ChoiceOperation -> {
                        val next = current
                        for ((index, branch) in operation.branches.withIndex()) {
                            search(Continuation(Execute(branch), next), HashMap(state), calls, decisions + (operation to index))
                        }
                        return
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
                            exploredPaths++
                            return
                        }
                    }
                    is AssignmentOperation -> {
                        val variable = variableOf(operation.reference)
                        val value = assigned(state, operation.reference, evaluator.evaluate(operation.expression), evaluator)
                        if (isTracer(variable) && value == true) {
                            calls = calls + variable
                        }
                        val expected = target[variable]
                        if (variable !in writtenAfter.getValue(operation) && expected != null && expected != value) {
                            exploredPaths++
                            return
                        }
                        state[variable] = value
                    }
                    is HavocOperation -> {
                        val variable = variableOf(operation.reference)
                        if (operation.reference !is ElementReference) {
                            throw Undecided("the havoc of an element of ${variable.name}")
                        }
                        val expected = target[variable]
                        if (variable !in writtenAfter.getValue(operation) && expected != null) {
                            state[variable] = expected
                        } else {
                            val candidates = domainOf(variable) ?: throw Undecided("the havoced integer ${variable.name} is unrecorded or written again in the step")
                            val next = current
                            for ((index, candidate) in candidates.withIndex()) {
                                val branchState = HashMap(state)
                                branchState[variable] = candidate
                                search(next, branchState, calls, decisions + (operation to index))
                            }
                            return
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

        private fun accept(state: Map<VariableDeclaration, Any>, calls: List<VariableDeclaration>, decisions: List<Pair<Operation, Int>>) {
            exploredPaths++
            if (exploredPaths > PATH_LIMIT) {
                throw Undecided("more than $PATH_LIMIT paths")
            }
            val fits = target.all { (variable, value) ->
                state[variable] == value
            }
            if (fits) {
                fitting += InterpretedPath(calls, decisions, state.filterKeys { it in globals })
            }
        }

        private fun assigned(state: Map<VariableDeclaration, Any>, reference: Expression, value: Any, evaluator: Evaluator): Any {
            if (reference !is IndexingSuffixExpression) {
                return value
            }
            val variable = variableOf(reference)
            val array = state[variable] as? List<*> ?: throw Undecided("${variable.name} is written by index before it has a value")
            val index = evaluator.evaluate(reference.index) as Int
            return array.toMutableList().also {
                it[index] = value
            }
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
            is EnumDeclaration -> domain.literals.map {
                enumValue(it)
            }
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

    private fun enumValue(literal: EnumLiteral): EnumValue {
        return EnumValue("${(literal.eContainer() as EnumDeclaration).name}.${literal.name}")
    }

    private inner class Evaluator(
        private val state: Map<VariableDeclaration, Any>,
    ) : BackendExpressionVisitor<Any>() {

        fun evaluate(expression: Expression): Any {
            exercised += expression.eClass().name
            return visit(expression)
        }

        override fun visit(expression: ComparisonOperator): Any {
            val left = evaluate(expression.left)
            val right = evaluate(expression.right)
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
            val left = evaluate(expression.left) as Int
            val right = evaluate(expression.right) as Int
            return when (expression.op) {
                ArithmeticOp.ADD -> left + right
                ArithmeticOp.SUB -> left - right
                ArithmeticOp.MUL -> left * right
                ArithmeticOp.DIV -> left / right
                null -> error("Arithmetic without an operator")
            }
        }

        override fun visit(expression: BooleanOperator): Any {
            val left = evaluate(expression.left) as Boolean
            return when (expression.op) {
                BooleanOp.AND -> left && evaluate(expression.right) as Boolean
                BooleanOp.OR -> left || evaluate(expression.right) as Boolean
                BooleanOp.XOR -> left xor evaluate(expression.right) as Boolean
                null -> error("Boolean operator without an operator")
            }
        }

        override fun visit(expression: ArithmeticUnaryOperator): Any {
            val body = evaluate(expression.body) as Int
            return when (expression.op) {
                UnaryOp.PLUS -> body
                UnaryOp.MINUS -> -body
                null -> error("Unary operator without an operator")
            }
        }

        override fun visit(expression: NegationOperator): Any {
            return !(evaluate(expression.body) as Boolean)
        }

        override fun visit(expression: IfThenElse): Any {
            return if (evaluate(expression.guard) as Boolean) {
                evaluate(expression.then)
            } else {
                evaluate(expression.`else`)
            }
        }

        override fun visit(expression: LiteralReal): Any {
            throw Undecided("a real literal")
        }

        override fun visit(expression: LiteralInteger): Any {
            return expression.value
        }

        override fun visit(expression: LiteralBoolean): Any {
            return expression.isValue
        }

        override fun visit(expression: ArrayLiteral): Any {
            return expression.values.map {
                evaluate(it)
            }
        }

        override fun visit(expression: ElementReference): Any {
            return when (val element = expression.element) {
                is EnumLiteral -> enumValue(element)
                is VariableDeclaration -> {
                    if (isTracer(element)) {
                        error("A trace variable is read: ${element.name}")
                    }
                    state[element] ?: throw Undecided("${element.name} is read before it has a value")
                }
                else -> error("Unexpected element: ${element.eClass().name}")
            }
        }

        override fun visit(expression: IndexingSuffixExpression): Any {
            val array = evaluate(expression.primary) as List<*>
            val index = evaluate(expression.index) as Int
            return array[index] ?: throw Undecided("index $index has no value")
        }
    }
}
