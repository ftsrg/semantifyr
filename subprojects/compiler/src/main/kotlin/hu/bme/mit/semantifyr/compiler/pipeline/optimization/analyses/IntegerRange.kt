/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.optimization.analyses

data class IntegerRange(
    val lower: Long?,
    val upper: Long?,
) {

    val isBounded: Boolean
        get() = lower != null && upper != null

    fun isWithin(lowest: Long, highest: Long): Boolean {
        return lower != null && upper != null && lower >= lowest && upper <= highest
    }

    operator fun contains(value: Long): Boolean {
        return (lower == null || lower <= value) && (upper == null || value <= upper)
    }

    operator fun contains(other: IntegerRange): Boolean {
        val lowerHolds = lower == null || (other.lower != null && lower <= other.lower)
        val upperHolds = upper == null || (other.upper != null && other.upper <= upper)
        return lowerHolds && upperHolds
    }

    infix fun join(other: IntegerRange): IntegerRange {
        return IntegerRange(
            lower = if (lower == null || other.lower == null) null else minOf(lower, other.lower),
            upper = if (upper == null || other.upper == null) null else maxOf(upper, other.upper),
        )
    }

    fun intersect(other: IntegerRange): IntegerRange? {
        val newLower = when {
            lower == null -> other.lower
            other.lower == null -> lower
            else -> maxOf(lower, other.lower)
        }
        val newUpper = when {
            upper == null -> other.upper
            other.upper == null -> upper
            else -> minOf(upper, other.upper)
        }
        if (newLower != null && newUpper != null && newLower > newUpper) {
            return null
        }
        return IntegerRange(newLower, newUpper)
    }

    operator fun plus(other: IntegerRange): IntegerRange {
        return IntegerRange(
            lower = exactOrNull(lower, other.lower, Math::addExact),
            upper = exactOrNull(upper, other.upper, Math::addExact),
        )
    }

    operator fun minus(other: IntegerRange): IntegerRange {
        return this + -other
    }

    operator fun unaryMinus(): IntegerRange {
        return IntegerRange(
            lower = upper?.takeIf { it != Long.MIN_VALUE }?.let { -it },
            upper = lower?.takeIf { it != Long.MIN_VALUE }?.let { -it },
        )
    }

    operator fun times(other: IntegerRange): IntegerRange {
        if (this == ZERO || other == ZERO) {
            return ZERO
        }
        val corners = corners(other, Math::multiplyExact) ?: return UNBOUNDED
        return IntegerRange(corners.min(), corners.max())
    }

    operator fun div(divisor: IntegerRange): IntegerRange {
        if (0L in divisor) {
            return UNBOUNDED
        }
        val dividendLower = lower ?: return UNBOUNDED
        val dividendUpper = upper ?: return UNBOUNDED
        if (!divisor.isBounded) {
            // the divisor excludes zero, so the quotient is no larger than the dividend
            val magnitude = (-this).upper?.let { maxOf(it, dividendUpper) } ?: return UNBOUNDED
            return IntegerRange(-magnitude, magnitude)
        }
        val floors = corners(divisor, Math::floorDivExact) ?: return UNBOUNDED
        val ceilings = corners(divisor, Math::ceilDivExact) ?: return UNBOUNDED
        return IntegerRange(floors.min(), ceilings.max())
    }

    override fun toString(): String {
        return "[${lower ?: "-inf"}, ${upper ?: "inf"}]"
    }

    private fun corners(other: IntegerRange, operation: (Long, Long) -> Long): List<Long>? {
        if (lower == null || upper == null || other.lower == null || other.upper == null) {
            return null
        }
        return listOf(lower to other.lower, lower to other.upper, upper to other.lower, upper to other.upper).map { (first, second) ->
            exactOrNull(first, second, operation) ?: return null
        }
    }

    private fun exactOrNull(
        first: Long?,
        second: Long?,
        operation: (Long, Long) -> Long,
    ): Long? {
        if (first == null || second == null) {
            return null
        }
        return try {
            operation(first, second)
        } catch (_: ArithmeticException) {
            null
        }
    }

    companion object {
        val UNBOUNDED = IntegerRange(null, null)
        val ZERO = of(0)

        fun of(value: Long): IntegerRange {
            return IntegerRange(value, value)
        }
    }

}
