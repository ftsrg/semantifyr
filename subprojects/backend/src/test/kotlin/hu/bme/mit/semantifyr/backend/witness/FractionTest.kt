/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backend.witness

import kotlinx.serialization.json.Json
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
    fun `a whole amount is written without a denominator`() {
        assertThat(Json.encodeToString(Fraction.of(4, 2))).isEqualTo("2")
        assertThat(Json.encodeToString(Fraction.ZERO)).isEqualTo("0")
    }

    @Test
    fun `a proper fraction is written as a string`() {
        assertThat(Json.encodeToString(Fraction.of(3, 2))).isEqualTo("\"3/2\"")
    }

    @Test
    fun `a whole amount too large for a JSON number is written as a string`() {
        val large = Fraction.of(BigInteger.TWO.pow(53))

        assertThat(Json.encodeToString(large)).isEqualTo("\"9007199254740992\"")
        assertThat(Json.encodeToString(Fraction.of(MAX_EXACT_JSON_INTEGER))).isEqualTo("9007199254740991")
    }

    @Test
    fun `every written form reads back`() {
        val fractions = listOf(
            Fraction.ZERO,
            Fraction.of(3, 2),
            Fraction.of(BigInteger.TWO.pow(64)),
        )
        for (fraction in fractions) {
            assertThat(Json.decodeFromString<Fraction>(Json.encodeToString(fraction))).isEqualTo(fraction)
        }
    }

    @Test
    fun `a zero denominator is no fraction`() {
        assertThrows<NumberFormatException> {
            Fraction.parse("1/0")
        }
    }
}
