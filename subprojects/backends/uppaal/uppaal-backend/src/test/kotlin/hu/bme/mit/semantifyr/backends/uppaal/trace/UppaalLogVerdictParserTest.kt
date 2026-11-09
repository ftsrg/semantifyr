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
    fun `an out-of-range assignment names its variable and value`() {
        val assignment = parser.parseOutOfRangeAssignment(
            sequenceOf("model.xml:/nta/template[1]/transition[3]/label[1]:1: [error] Assignment of value '-32769' to variable 'x' is out of range."),
        )

        assertThat(assignment!!.variable).isEqualTo("x")
        assertThat(assignment.value).isEqualTo("-32769")
    }

    @Test
    fun `an out-of-range initial value is no out-of-range assignment`() {
        assertThat(parser.parseOutOfRangeAssignment(sequenceOf("model.xml:/nta/declaration:1: [error] Initial value out of range."))).isNull()
    }

    @Test
    fun `case-insensitive match`() {
        val verdict = parser.parse(sequenceOf("FORMULA IS SATISFIED"))
        assertThat(verdict).isEqualTo(UppaalVerdict.Satisfied)
    }

    @Test
    fun `a model verifyta rejects names verifyta's message`() {
        val rejection = parser.rejection(sequenceOf("model.xml:/nta/declaration:2: [error] Duplicate definition of On."))

        assertThat(rejection).contains("Duplicate definition of On.")
    }

    @Test
    fun `a licence refusal names verifyta's message`() {
        val rejection = parser.rejection(
            sequenceOf(
                "license key is not set",
                "Please use --key option to specify a valid license key.",
            ),
        )

        assertThat(rejection).contains("license key is not set")
    }

    @Test
    fun `a verification that ran has no rejection`() {
        val rejection = parser.rejection(
            sequenceOf(
                "Verifying formula 1 at model.q:1",
                " -- Formula is NOT satisfied.",
            ),
        )

        assertThat(rejection).isNull()
    }

}
