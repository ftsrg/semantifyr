/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backend.witness

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigInteger

class FractionTest {

    @Test
    fun `a fraction is kept in lowest terms with the sign on the numerator`() {
        assertThat(Fraction.of(6, -4)).isEqualTo(Fraction.of(-3, 2))
        assertThat(Fraction.of(6, -4).toString()).isEqualTo("-3/2")
    }

    @Test
    fun `a sum of fractions is in lowest terms`() {
        assertThat(Fraction.of(1, 6) + Fraction.of(1, 3)).isEqualTo(Fraction.of(1, 2))
        assertThat(Fraction.of(1, 2) + Fraction.of(1, 2)).isEqualTo(Fraction.of(1))
    }

    @Test
    fun `a fraction reads back from its text`() {
        val fractions = listOf(
            Fraction.ZERO,
            Fraction.of(-3, 2),
            Fraction.of(BigInteger.TWO.pow(64)),
        )
        for (fraction in fractions) {
            assertThat(Fraction.parse(fraction.toString())).isEqualTo(fraction)
        }
    }

    @Test
    fun `a zero denominator is no fraction`() {
        assertThrows<NumberFormatException> {
            Fraction.parse("1/0")
        }
    }
}
