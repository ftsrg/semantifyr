/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.optimization.analyses

import hu.bme.mit.semantifyr.compiler.pipeline.expression.tryEvaluateTypedOrNull
import hu.bme.mit.semantifyr.compiler.pipeline.utils.eAllOfType
import hu.bme.mit.semantifyr.oxsts.lang.library.builtin.BuiltinSymbolResolver
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.MetaConstantExpressionEvaluator
import hu.bme.mit.semantifyr.oxsts.lang.utils.OxstsUtils
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
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ForOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.HavocOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.IfOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.IfThenElse
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralBoolean
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralInteger
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LocalVarDeclarationOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.NegationOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Operation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.RangeExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ReferenceExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.SequenceOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.TransitionDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.UnaryOp
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration
import java.util.TreeSet

// every value an integer variable or an arithmetic expression takes anywhere in a run; code no run reaches has no entry
data class VariableBoundsInfo(
    val variableRanges: Map<VariableDeclaration, IntegerRange>,
    val arithmeticRanges: Map<Expression, IntegerRange>,
)

class VariableBoundsComputation(
    private val inlinedOxsts: InlinedOxsts,
    private val evaluator: MetaConstantExpressionEvaluator,
    builtinSymbolResolver: BuiltinSymbolResolver,
    private val havocRange: (VariableDeclaration) -> IntegerRange = { IntegerRange.UNBOUNDED },
    private val limit: (VariableDeclaration) -> IntegerRange = { IntegerRange.UNBOUNDED },
) {

    private val intDatatype = builtinSymbolResolver.intDatatype(inlinedOxsts)
    private val integerVariables = mutableMapOf<VariableDeclaration, Boolean>()
    private val globals = inlinedOxsts.variables.filter {
        isIntegerVariable(it)
    }.toSet()
    private val thresholds = collectThresholds()

    private var recording = false
    private val variableRanges = LinkedHashMap<VariableDeclaration, IntegerRange>()
    private val arithmeticRanges = LinkedHashMap<Expression, IntegerRange>()

    fun compute(): VariableBoundsInfo {
        val reachable = reachableStates(initialState())

        recording = true
        val initial = initialState()
        execute(inlinedOxsts.initTransition, initial)
        if (reachable != null) {
            execute(inlinedOxsts.mainTransition, reachable)
            evaluate(inlinedOxsts.property.expression, reachable)
        }

        return VariableBoundsInfo(variableRanges, arithmeticRanges)
    }

    private fun reachableStates(initial: MutableMap<VariableDeclaration, IntegerRange>): MutableMap<VariableDeclaration, IntegerRange>? {
        val afterInit = execute(inlinedOxsts.initTransition, initial) ?: return null

        var states: MutableMap<VariableDeclaration, IntegerRange> = afterInit
        var round = 0
        while (true) {
            val next = join(states, execute(inlinedOxsts.mainTransition, states))!!
            if (isWithin(next, states)) {
                break
            }
            states = if (round < WIDENING_DELAY) {
                next
            } else {
                widen(states, next)
            }
            round++
        }

        repeat(NARROWING_ROUNDS) {
            val next = join(afterInit, execute(inlinedOxsts.mainTransition, states))!!
            states = states.mapValuesTo(HashMap()) { (variable, range) ->
                next[variable]?.let {
                    range.intersect(it)
                } ?: range
            }
        }
        return states
    }

    private fun initialState(): MutableMap<VariableDeclaration, IntegerRange> {
        val state = HashMap<VariableDeclaration, IntegerRange>()
        for (variable in globals) {
            val initializer = variable.expression ?: error("Variable ${variable.name} has no initializer")
            write(state, variable, evaluate(initializer, state))
        }
        return state
    }

    private fun execute(transition: TransitionDeclaration, state: Map<VariableDeclaration, IntegerRange>): MutableMap<VariableDeclaration, IntegerRange>? {
        if (transition.branches.isEmpty()) {
            return HashMap(state)
        }
        val result = transition.branches.map {
            execute(it, HashMap(state))
        }.reduce { first, second ->
            join(first, second)
        } ?: return null
        result.keys.retainAll(globals)
        return result
    }

    private fun execute(operation: Operation, state: MutableMap<VariableDeclaration, IntegerRange>): MutableMap<VariableDeclaration, IntegerRange>? {
        return when (operation) {
            is SequenceOperation -> {
                var current = state
                for (step in operation.steps) {
                    current = execute(step, current) ?: return null
                }
                current
            }
            is ChoiceOperation -> {
                operation.branches.map {
                    execute(it, HashMap(state))
                }.reduce { first, second ->
                    join(first, second)
                }
            }
            is IfOperation -> {
                val thenState = refine(HashMap(state), operation.guard, true)?.let {
                    execute(operation.body, it)
                }
                val elseState = refine(state, operation.guard, false)?.let { refined ->
                    operation.`else`?.let {
                        execute(it, refined)
                    } ?: refined
                }
                join(thenState, elseState)
            }
            is AssumptionOperation -> refine(state, operation.expression, true)
            is AssignmentOperation -> {
                val value = evaluate(operation.expression, state)
                val variable = integerVariableOf(operation.reference)
                if (variable != null) {
                    write(state, variable, value)
                } else if (variableOf(operation.reference) == null) {
                    return havocEverything(state, operation)
                }
                state
            }
            is HavocOperation -> {
                val variable = integerVariableOf(operation.reference)
                if (variable != null) {
                    write(state, variable, havocRange(variable))
                } else if (variableOf(operation.reference) == null) {
                    return havocEverything(state, operation)
                }
                state
            }
            is LocalVarDeclarationOperation -> {
                val initializer = operation.expression ?: error("Local variable ${operation.name} has no initializer")
                val value = evaluate(initializer, state)
                if (isIntegerVariable(operation)) {
                    write(state, operation, value)
                }
                state
            }
            is ForOperation -> executeLoop(operation, state)
            else -> havocEverything(state, operation)
        }
    }

    private fun executeLoop(operation: ForOperation, state: MutableMap<VariableDeclaration, IntegerRange>): MutableMap<VariableDeclaration, IntegerRange>? {
        val loopVariable = operation.loopVariable
        val rangeExpression = operation.rangeExpression
        val iterations = if (rangeExpression is RangeExpression) {
            val first = evaluate(rangeExpression.left, state)
            val end = evaluate(rangeExpression.right, state)
            val last = if (rangeExpression.isExclusive) {
                end - IntegerRange.of(1)
            } else {
                end
            }
            IntegerRange(first.lower, last.upper) to constantIterations(first, last)
        } else {
            evaluate(rangeExpression, state)
            IntegerRange.UNBOUNDED to null
        }
        val (loopRange, constant) = iterations

        if (constant != null) {
            var current = state
            for (value in constant) {
                write(current, loopVariable, IntegerRange.of(value))
                current = execute(operation.body, current) ?: return null
            }
            return current
        }

        var head: MutableMap<VariableDeclaration, IntegerRange> = state
        var round = 0
        while (true) {
            val bodyState = HashMap(head)
            write(bodyState, loopVariable, loopRange)
            val next = join(head, execute(operation.body, bodyState))!!
            next.keys.retainAll(head.keys)
            if (isWithin(next, head)) {
                return head
            }
            head = if (round < WIDENING_DELAY) {
                next
            } else {
                widen(head, next)
            }
            round++
        }
    }

    private fun constantIterations(first: IntegerRange, last: IntegerRange): LongRange? {
        val from = first.lower?.takeIf { first.upper == it } ?: return null
        val to = last.lower?.takeIf { last.upper == it } ?: return null
        if (to - from >= UNROLLED_ITERATIONS) {
            return null
        }
        return from..to
    }

    private fun havocEverything(state: MutableMap<VariableDeclaration, IntegerRange>, operation: Operation): MutableMap<VariableDeclaration, IntegerRange> {
        for (expression in operation.eAllOfType<Expression>()) {
            if (expression is ArithmeticBinaryOperator || expression is ArithmeticUnaryOperator) {
                record(expression, IntegerRange.UNBOUNDED)
            }
        }
        for (variable in state.keys + operation.eAllOfType<VariableDeclaration>().filter { isIntegerVariable(it) }) {
            write(state, variable, IntegerRange.UNBOUNDED)
        }
        return state
    }

    private fun refine(
        state: MutableMap<VariableDeclaration, IntegerRange>,
        guard: Expression,
        polarity: Boolean,
    ): MutableMap<VariableDeclaration, IntegerRange>? {
        return when {
            guard is LiteralBoolean -> state.takeIf { guard.isValue == polarity }
            guard is NegationOperator -> refine(state, guard.body, !polarity)
            guard is BooleanOperator && guard.op != BooleanOp.XOR -> {
                val conjunction = (guard.op == BooleanOp.AND) == polarity
                if (conjunction) {
                    refine(state, guard.left, polarity)?.let {
                        refine(it, guard.right, polarity)
                    }
                } else {
                    join(refine(HashMap(state), guard.left, polarity), refine(state, guard.right, polarity))
                }
            }
            guard is ComparisonOperator && isIntegerExpression(guard.left) && isIntegerExpression(guard.right) -> {
                refineComparison(state, guard, polarity)
            }
            else -> {
                evaluate(guard, state)
                state
            }
        }
    }

    private fun refineComparison(
        state: MutableMap<VariableDeclaration, IntegerRange>,
        comparison: ComparisonOperator,
        polarity: Boolean,
    ): MutableMap<VariableDeclaration, IntegerRange>? {
        val operator = if (polarity) {
            comparison.op
        } else {
            negate(comparison.op)
        }
        val left = evaluate(comparison.left, state)
        val right = evaluate(comparison.right, state)
        if (!isSatisfiable(left, operator, right)) {
            return null
        }
        integerVariableOf(comparison.left)?.let {
            state[it] = constrain(left, operator, right) ?: return null
        }
        integerVariableOf(comparison.right)?.let {
            state[it] = constrain(right, flip(operator), left) ?: return null
        }
        return state
    }

    // the values of range that stand in relation operator to some value of bound
    private fun constrain(
        range: IntegerRange,
        operator: ComparisonOp,
        bound: IntegerRange,
    ): IntegerRange? {
        val constraint = when (operator) {
            ComparisonOp.LESS -> IntegerRange(null, bound.upper?.let { it - 1 })
            ComparisonOp.LESS_EQ -> IntegerRange(null, bound.upper)
            ComparisonOp.GREATER -> IntegerRange(bound.lower?.let { it + 1 }, null)
            ComparisonOp.GREATER_EQ -> IntegerRange(bound.lower, null)
            ComparisonOp.EQ -> bound
            ComparisonOp.NOT_EQ -> {
                val excluded = bound.lower?.takeIf { bound.upper == it } ?: return range
                return when (excluded) {
                    range.lower -> range.intersect(IntegerRange(excluded + 1, null))
                    range.upper -> range.intersect(IntegerRange(null, excluded - 1))
                    else -> range
                }
            }
        }
        return range.intersect(constraint)
    }

    private fun isSatisfiable(
        left: IntegerRange,
        operator: ComparisonOp,
        right: IntegerRange,
    ): Boolean {
        return when (operator) {
            ComparisonOp.LESS -> left.lower == null || right.upper == null || left.lower < right.upper
            ComparisonOp.LESS_EQ -> left.lower == null || right.upper == null || left.lower <= right.upper
            ComparisonOp.GREATER -> left.upper == null || right.lower == null || left.upper > right.lower
            ComparisonOp.GREATER_EQ -> left.upper == null || right.lower == null || left.upper >= right.lower
            ComparisonOp.EQ -> left.intersect(right) != null
            ComparisonOp.NOT_EQ -> !(left == right && left.lower != null && left.lower == left.upper)
        }
    }

    private fun negate(operator: ComparisonOp): ComparisonOp {
        return when (operator) {
            ComparisonOp.LESS -> ComparisonOp.GREATER_EQ
            ComparisonOp.LESS_EQ -> ComparisonOp.GREATER
            ComparisonOp.GREATER -> ComparisonOp.LESS_EQ
            ComparisonOp.GREATER_EQ -> ComparisonOp.LESS
            ComparisonOp.EQ -> ComparisonOp.NOT_EQ
            ComparisonOp.NOT_EQ -> ComparisonOp.EQ
        }
    }

    private fun flip(operator: ComparisonOp): ComparisonOp {
        return when (operator) {
            ComparisonOp.LESS -> ComparisonOp.GREATER
            ComparisonOp.LESS_EQ -> ComparisonOp.GREATER_EQ
            ComparisonOp.GREATER -> ComparisonOp.LESS
            ComparisonOp.GREATER_EQ -> ComparisonOp.LESS_EQ
            ComparisonOp.EQ -> ComparisonOp.EQ
            ComparisonOp.NOT_EQ -> ComparisonOp.NOT_EQ
        }
    }

    private fun evaluate(expression: Expression, state: Map<VariableDeclaration, IntegerRange>): IntegerRange {
        return when (expression) {
            is LiteralInteger -> IntegerRange.of(expression.value.toLong())
            is ArithmeticUnaryOperator -> {
                val body = evaluate(expression.body, state)
                val value = when (expression.op) {
                    UnaryOp.PLUS -> body
                    UnaryOp.MINUS -> -body
                }
                record(expression, value)
            }
            is ArithmeticBinaryOperator -> {
                val left = evaluate(expression.left, state)
                val right = evaluate(expression.right, state)
                val value = when (expression.op) {
                    ArithmeticOp.ADD -> left + right
                    ArithmeticOp.SUB -> left - right
                    ArithmeticOp.MUL -> left * right
                    ArithmeticOp.DIV -> left / right
                }
                record(expression, value)
            }
            is IfThenElse -> {
                val thenValue = refine(HashMap(state), expression.guard, true)?.let {
                    evaluate(expression.then, it)
                }
                val elseValue = refine(HashMap(state), expression.guard, false)?.let {
                    evaluate(expression.`else`, it)
                }
                when {
                    thenValue == null -> elseValue ?: IntegerRange.UNBOUNDED
                    elseValue == null -> thenValue
                    else -> thenValue join elseValue
                }
            }
            else -> {
                val variable = integerVariableOf(expression)
                if (variable != null) {
                    state[variable] ?: IntegerRange.UNBOUNDED
                } else {
                    for (child in expression.eContents().filterIsInstance<Expression>()) {
                        evaluate(child, state)
                    }
                    IntegerRange.UNBOUNDED
                }
            }
        }
    }

    private fun write(
        state: MutableMap<VariableDeclaration, IntegerRange>,
        variable: VariableDeclaration,
        range: IntegerRange,
    ) {
        val value = limited(variable, range)
        state[variable] = value
        if (recording) {
            variableRanges[variable] = variableRanges[variable]?.join(value) ?: value
        }
    }

    // an engine that stops on a value outside a variable's limit goes on only with values within it
    private fun limited(variable: VariableDeclaration, range: IntegerRange): IntegerRange {
        val bound = limit(variable)
        return range.intersect(bound) ?: bound
    }

    private fun record(expression: Expression, range: IntegerRange): IntegerRange {
        if (recording) {
            arithmeticRanges[expression] = arithmeticRanges[expression]?.join(range) ?: range
        }
        return range
    }

    private fun join(first: Map<VariableDeclaration, IntegerRange>?, second: Map<VariableDeclaration, IntegerRange>?): MutableMap<VariableDeclaration, IntegerRange>? {
        if (first == null) {
            return second?.let {
                HashMap(it)
            }
        }
        val result = HashMap(first)
        for ((variable, range) in second.orEmpty()) {
            result[variable] = result[variable]?.join(range) ?: range
        }
        return result
    }

    private fun isWithin(next: Map<VariableDeclaration, IntegerRange>, current: Map<VariableDeclaration, IntegerRange>): Boolean {
        return next.all { (variable, range) ->
            current[variable]?.let {
                range in it
            } ?: false
        }
    }

    private fun widen(previous: Map<VariableDeclaration, IntegerRange>, next: Map<VariableDeclaration, IntegerRange>): MutableMap<VariableDeclaration, IntegerRange> {
        return next.mapValuesTo(HashMap()) { (variable, range) ->
            val old = previous[variable] ?: return@mapValuesTo range
            val widened = IntegerRange(
                lower = if (range.lower == old.lower) {
                    range.lower
                } else {
                    range.lower?.let {
                        thresholds.floor(it)
                    }
                },
                upper = if (range.upper == old.upper) {
                    range.upper
                } else {
                    range.upper?.let {
                        thresholds.ceiling(it)
                    }
                },
            )
            limited(variable, widened)
        }
    }

    // a guard's literal, and that literal stepped once by a constant the model adds, bound most counters
    private fun collectThresholds(): TreeSet<Long> {
        val literals = TreeSet(listOf(0L))
        for (literal in inlinedOxsts.eAllOfType<LiteralInteger>()) {
            val value = literal.value.toLong()
            literals += listOf(value - 1, value, value + 1, -value - 1, -value, -value + 1)
        }
        val steps = inlinedOxsts.eAllOfType<ArithmeticBinaryOperator>().filter {
            it.op == ArithmeticOp.ADD || it.op == ArithmeticOp.SUB
        }.flatMap {
            listOf(it.left, it.right)
        }.filterIsInstance<LiteralInteger>().map {
            it.value.toLong()
        }.toSet()
        val result = TreeSet(literals)
        for (step in steps) {
            for (literal in literals) {
                result += literal + step
                result += literal - step
            }
        }
        return result
    }

    private fun isIntegerExpression(expression: Expression): Boolean {
        return when (expression) {
            is LiteralInteger, is ArithmeticBinaryOperator, is ArithmeticUnaryOperator -> true
            is IfThenElse -> isIntegerExpression(expression.then)
            else -> integerVariableOf(expression) != null
        }
    }

    private fun integerVariableOf(expression: Expression): VariableDeclaration? {
        return variableOf(expression)?.takeIf {
            isIntegerVariable(it)
        }
    }

    private fun variableOf(expression: Expression): VariableDeclaration? {
        if (expression !is ReferenceExpression) {
            return null
        }
        return evaluator.tryEvaluateTypedOrNull(VariableDeclaration::class.java, expression)
    }

    private fun isIntegerVariable(variable: VariableDeclaration): Boolean {
        return integerVariables.getOrPut(variable) {
            val domain = variable.typeSpecification?.domain
            when {
                domain != null -> domain === intDatatype
                OxstsUtils.isLoopVariable(variable) -> true
                else -> variable.expression?.let {
                    isIntegerExpression(it)
                } ?: false
            }
        }
    }

    companion object {
        private const val WIDENING_DELAY = 5
        private const val NARROWING_ROUNDS = 2
        private const val UNROLLED_ITERATIONS = 64
    }

}
