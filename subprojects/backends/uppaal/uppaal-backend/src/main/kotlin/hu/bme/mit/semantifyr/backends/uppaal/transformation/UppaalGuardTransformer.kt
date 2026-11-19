/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.uppaal.transformation

import com.google.inject.Inject
import hu.bme.mit.semantifyr.backend.BackendUnsupportedException
import hu.bme.mit.semantifyr.oxsts.lang.semantics.clock.ClockTestAnalysis
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.ConstantExpressionEvaluatorProvider
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.EvaluationFailureException
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.IntegerEvaluation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.BooleanOp
import hu.bme.mit.semantifyr.oxsts.model.oxsts.BooleanOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ComparisonOp
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ComparisonOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.IfThenElse
import hu.bme.mit.semantifyr.oxsts.model.oxsts.NegationOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration

enum class UppaalClockOp(
    val symbol: String,
) {
    Less("<"),
    LessEqual("<="),
    Greater(">"),
    GreaterEqual(">="),
    Equal("=="),
}

class UppaalClockAtom(
    val clock: VariableDeclaration,
    val op: UppaalClockOp,
    val bound: Long,
) {
    fun holdsAt(value: Long): Boolean {
        return when (op) {
            UppaalClockOp.Less -> value < bound
            UppaalClockOp.LessEqual -> value <= bound
            UppaalClockOp.Greater -> value > bound
            UppaalClockOp.GreaterEqual -> value >= bound
            UppaalClockOp.Equal -> value == bound
        }
    }
}

class UppaalGuard(
    val conditions: List<String>,
    val clockAtoms: List<UppaalClockAtom>,
) {
    operator fun plus(other: UppaalGuard): UppaalGuard {
        return UppaalGuard(conditions + other.conditions, clockAtoms + other.clockAtoms)
    }
}

class UppaalGuardTransformer @Inject constructor(
    private val uppaalExpressionTransformer: UppaalExpressionTransformer,
    private val uppaalVariableTransformer: UppaalVariableTransformer,
    private val clockTestAnalysis: ClockTestAnalysis,
    private val constantExpressionEvaluatorProvider: ConstantExpressionEvaluatorProvider,
) {

    fun disjunctsOf(expression: Expression): List<UppaalGuard> {
        return normalize(expression, true)
    }

    fun disjunctsOfNegated(expression: Expression): List<UppaalGuard> {
        return normalize(expression, false)
    }

    fun render(guard: UppaalGuard): String? {
        val atoms = guard.clockAtoms.map {
            "${uppaalVariableTransformer.nameOf(it.clock)} ${it.op.symbol} ${it.bound}"
        }
        val parts = guard.conditions + atoms
        if (parts.isEmpty()) {
            return null
        }
        return parts.joinToString(" && ")
    }

    private fun normalize(expression: Expression, positive: Boolean): List<UppaalGuard> {
        if (!readsClock(expression)) {
            val rendered = uppaalExpressionTransformer.transform(expression)
            val condition = if (positive) {
                rendered
            } else {
                "!($rendered)"
            }
            return listOf(UppaalGuard(listOf(condition), emptyList()))
        }
        return when (expression) {
            is NegationOperator -> normalize(expression.body, !positive)
            is BooleanOperator -> normalizeBoolean(expression, positive)
            is IfThenElse -> {
                union(
                    product(normalize(expression.guard, true), normalize(expression.then, positive)),
                    product(normalize(expression.guard, false), normalize(expression.`else`, positive)),
                )
            }
            is ComparisonOperator -> normalizeClockTest(expression, positive)
            else -> throw BackendUnsupportedException("Uppaal reads a clock only in comparisons with a constant")
        }
    }

    private fun normalizeBoolean(expression: BooleanOperator, positive: Boolean): List<UppaalGuard> {
        val left = expression.left
        val right = expression.right
        return when (expression.op) {
            BooleanOp.AND -> if (positive) {
                product(normalize(left, true), normalize(right, true))
            } else {
                union(normalize(left, false), normalize(right, false))
            }
            BooleanOp.OR -> if (positive) {
                union(normalize(left, true), normalize(right, true))
            } else {
                product(normalize(left, false), normalize(right, false))
            }
            BooleanOp.XOR -> union(
                product(normalize(left, true), normalize(right, !positive)),
                product(normalize(left, false), normalize(right, positive)),
            )
        }
    }

    private fun normalizeClockTest(comparison: ComparisonOperator, positive: Boolean): List<UppaalGuard> {
        val clockTest = clockTestAnalysis.clockTest(comparison) ?: throw BackendUnsupportedException("Uppaal reads a clock only in comparisons with a constant")
        if (readsClock(clockTest.bound)) {
            throw BackendUnsupportedException("Uppaal compares a clock only with a constant")
        }
        val op = if (positive) {
            clockTest.operator
        } else {
            ClockTestAnalysis.negated(clockTest.operator)
        }
        val bound = evaluateBound(clockTest.bound)
        // Uppaal refuses != on a clock
        val ops = when (op) {
            ComparisonOp.LESS -> listOf(UppaalClockOp.Less)
            ComparisonOp.LESS_EQ -> listOf(UppaalClockOp.LessEqual)
            ComparisonOp.GREATER -> listOf(UppaalClockOp.Greater)
            ComparisonOp.GREATER_EQ -> listOf(UppaalClockOp.GreaterEqual)
            ComparisonOp.EQ -> listOf(UppaalClockOp.Equal)
            ComparisonOp.NOT_EQ -> listOf(
                UppaalClockOp.Less,
                UppaalClockOp.Greater,
            )
        }
        return leavesOf(clockTest.clock).flatMap { leaf ->
            ops.flatMap {
                product(leaf.guards, listOf(UppaalGuard(emptyList(), listOf(UppaalClockAtom(leaf.clock, it, bound)))))
            }
        }
    }

    private class ClockLeaf(
        val guards: List<UppaalGuard>,
        val clock: VariableDeclaration,
    )

    private fun leavesOf(expression: Expression): List<ClockLeaf> {
        if (expression is IfThenElse) {
            val thenLeaves = leavesOf(expression.then).map {
                ClockLeaf(product(normalize(expression.guard, true), it.guards), it.clock)
            }
            val elseLeaves = leavesOf(expression.`else`).map {
                ClockLeaf(product(normalize(expression.guard, false), it.guards), it.clock)
            }
            return thenLeaves + elseLeaves
        }
        val clock = clockTestAnalysis.referencedClock(expression) ?: error("The validator refuses a clock side with a branch that is no clock")
        return listOf(ClockLeaf(listOf(UppaalGuard(emptyList(), emptyList())), clock))
    }

    private fun evaluateBound(expression: Expression): Long {
        val evaluation = try {
            constantExpressionEvaluatorProvider.evaluate(expression)
        } catch (_: EvaluationFailureException) {
            throw BackendUnsupportedException("Uppaal compares a clock only with a constant integer")
        }
        if (evaluation !is IntegerEvaluation) {
            throw BackendUnsupportedException("Uppaal compares a clock only with a constant integer")
        }
        return evaluation.value.toLong()
    }

    private fun readsClock(expression: Expression): Boolean {
        if (clockTestAnalysis.referencedClock(expression) != null) {
            return true
        }
        return expression.eAllContents().asSequence().any {
            it is Expression && clockTestAnalysis.referencedClock(it) != null
        }
    }

    private fun product(left: List<UppaalGuard>, right: List<UppaalGuard>): List<UppaalGuard> {
        return left.flatMap { leftGuard ->
            right.map {
                leftGuard + it
            }
        }
    }

    private fun union(left: List<UppaalGuard>, right: List<UppaalGuard>): List<UppaalGuard> {
        return left + right
    }
}
