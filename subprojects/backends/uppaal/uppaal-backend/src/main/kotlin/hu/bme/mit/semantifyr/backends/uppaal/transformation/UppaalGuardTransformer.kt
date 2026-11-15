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

class UppaalClockAtom(
    val clock: VariableDeclaration,
    val op: ComparisonOp,
    val bound: Long,
) {
    fun holdsAt(value: Long): Boolean {
        return when (op) {
            ComparisonOp.LESS -> value < bound
            ComparisonOp.LESS_EQ -> value <= bound
            ComparisonOp.GREATER -> value > bound
            ComparisonOp.GREATER_EQ -> value >= bound
            ComparisonOp.EQ -> value == bound
            ComparisonOp.NOT_EQ -> value != bound
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

// Uppaal takes a clock only in a conjunction of bounds, so a condition over clocks becomes a disjunction of such guards
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
            "${uppaalVariableTransformer.nameOf(it.clock)} ${uppaalExpressionTransformer.operatorOf(it.op)} ${it.bound}"
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
        val clockOnLeft = clockTestAnalysis.isClockValued(comparison.left)
        val clockSide = if (clockOnLeft) {
            comparison.left
        } else {
            comparison.right
        }
        val boundSide = if (clockOnLeft) {
            comparison.right
        } else {
            comparison.left
        }
        if (readsClock(boundSide)) {
            throw BackendUnsupportedException("Uppaal compares a clock only with a constant")
        }
        val oriented = if (clockOnLeft) {
            comparison.op
        } else {
            mirrored(comparison.op)
        }
        val op = if (positive) {
            oriented
        } else {
            negated(oriented)
        }
        val bound = evaluateBound(boundSide)
        // Uppaal refuses != on a clock
        val ops = if (op == ComparisonOp.NOT_EQ) {
            listOf(
                ComparisonOp.LESS,
                ComparisonOp.GREATER,
            )
        } else {
            listOf(op)
        }
        return leavesOf(clockSide).flatMap { leaf ->
            ops.flatMap {
                product(leaf.guards, listOf(UppaalGuard(emptyList(), listOf(UppaalClockAtom(leaf.clock, it, bound)))))
            }
        }
    }

    private class ClockLeaf(
        val guards: List<UppaalGuard>,
        val clock: VariableDeclaration,
    )

    // a clock read through a variable holding an instance is a conditional over the candidates' clocks
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
        val clock = clockTestAnalysis.referencedClock(expression) ?: throw BackendUnsupportedException("Uppaal reads a clock only in comparisons with a constant")
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

    private fun mirrored(op: ComparisonOp): ComparisonOp {
        return when (op) {
            ComparisonOp.LESS -> ComparisonOp.GREATER
            ComparisonOp.LESS_EQ -> ComparisonOp.GREATER_EQ
            ComparisonOp.GREATER -> ComparisonOp.LESS
            ComparisonOp.GREATER_EQ -> ComparisonOp.LESS_EQ
            ComparisonOp.EQ -> ComparisonOp.EQ
            ComparisonOp.NOT_EQ -> ComparisonOp.NOT_EQ
        }
    }

    private fun negated(op: ComparisonOp): ComparisonOp {
        return when (op) {
            ComparisonOp.LESS -> ComparisonOp.GREATER_EQ
            ComparisonOp.LESS_EQ -> ComparisonOp.GREATER
            ComparisonOp.GREATER -> ComparisonOp.LESS_EQ
            ComparisonOp.GREATER_EQ -> ComparisonOp.LESS
            ComparisonOp.EQ -> ComparisonOp.NOT_EQ
            ComparisonOp.NOT_EQ -> ComparisonOp.EQ
        }
    }
}
