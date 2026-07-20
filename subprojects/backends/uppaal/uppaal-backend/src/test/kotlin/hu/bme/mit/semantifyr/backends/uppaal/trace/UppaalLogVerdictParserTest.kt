/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.uppaal.trace

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class UppaalLogVerdictParserTest {

    private val parser = UppaalLogVerdictParser()

    @Test
    fun `'Formula is satisfied' yields Satisfied`() {
        val verdict = parser.parse(sequenceOf("Verifying formula 1", " -- Formula is satisfied"))
        assertThat(verdict).isEqualTo(UppaalVerdict.Satisfied)
    }

    @Test
    fun `'Formula is NOT satisfied' yields Unsatisfied`() {
        val verdict = parser.parse(sequenceOf(" -- Formula is NOT satisfied"))
        assertThat(verdict).isEqualTo(UppaalVerdict.Unsatisfied)
    }

    @Test
    fun `'Property is satisfied' is recognised as Satisfied`() {
        val verdict = parser.parse(sequenceOf("Property is satisfied."))
        assertThat(verdict).isEqualTo(UppaalVerdict.Satisfied)
    }

    @Test
    fun `'Property is NOT satisfied' is recognised as Unsatisfied`() {
        val verdict = parser.parse(sequenceOf("Property is NOT satisfied."))
        assertThat(verdict).isEqualTo(UppaalVerdict.Unsatisfied)
    }

    @Test
    fun `the first decisive line wins`() {
        val verdict = parser.parse(
            sequenceOf(
                "Property is NOT satisfied",
                "Formula is satisfied",
            ),
        )
        assertThat(verdict).isEqualTo(UppaalVerdict.Unsatisfied)
    }

    @Test
    fun `unrelated lines yield null`() {
        assertThat(parser.parse(sequenceOf("Verifying ...", "Generating witness ..."))).isNull()
    }

    @Test
    fun `empty input yields null`() {
        assertThat(parser.parse(emptySequence())).isNull()
    }

    @Test
    fun `case-insensitive match`() {
        val verdict = parser.parse(sequenceOf("FORMULA IS SATISFIED"))
        assertThat(verdict).isEqualTo(UppaalVerdict.Satisfied)
    }
}
