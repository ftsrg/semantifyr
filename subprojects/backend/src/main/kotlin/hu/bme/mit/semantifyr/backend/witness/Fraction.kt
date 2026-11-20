/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backend.witness

import java.math.BigInteger

@ConsistentCopyVisibility
data class Fraction private constructor(
    val numerator: BigInteger,
    val denominator: BigInteger,
) {
    val isWhole: Boolean
        get() = denominator == BigInteger.ONE

    operator fun plus(other: Fraction): Fraction {
        return of(numerator * other.denominator + other.numerator * denominator, denominator * other.denominator)
    }

    override fun toString(): String {
        if (isWhole) {
            return numerator.toString()
        }
        return "$numerator/$denominator"
    }

    companion object {
        val ZERO = of(BigInteger.ZERO)

        fun of(numerator: BigInteger, denominator: BigInteger = BigInteger.ONE): Fraction {
            require(denominator.signum() != 0) {
                "a fraction has a nonzero denominator"
            }
            val divisor = numerator.gcd(denominator).let {
                if (denominator.signum() < 0) {
                    it.negate()
                } else {
                    it
                }
            }
            return Fraction(numerator / divisor, denominator / divisor)
        }

        fun of(numerator: Long, denominator: Long = 1): Fraction {
            return of(numerator.toBigInteger(), denominator.toBigInteger())
        }

        fun parse(text: String): Fraction {
            val parts = text.split('/')
            val numerator = parts[0].toBigIntegerOrNull()
            val denominator = parts.getOrNull(1)?.toBigIntegerOrNull() ?: BigInteger.ONE
            if (numerator == null || parts.size > 2 || denominator.signum() == 0) {
                throw NumberFormatException("'$text' is no fraction")
            }
            return of(numerator, denominator)
        }
    }
}
