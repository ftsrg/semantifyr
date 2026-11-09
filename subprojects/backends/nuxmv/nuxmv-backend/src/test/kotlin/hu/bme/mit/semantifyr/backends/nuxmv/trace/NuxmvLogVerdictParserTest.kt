/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.nuxmv.trace

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class NuxmvLogVerdictParserTest {

    private val parser = NuxmvLogVerdictParser()

    @Test
    fun `invariant followed by 'is true' on same line yields True`() {
        val verdict = parser.parse(sequenceOf("-- invariant (x = 5) is true"))
        assertThat(verdict).isEqualTo(NuxmvVerdict.True)
    }

    @Test
    fun `invariant followed by 'is false' on same line yields False`() {
        val verdict = parser.parse(sequenceOf("-- invariant (x = 5) is false"))
        assertThat(verdict).isEqualTo(NuxmvVerdict.False)
    }

    @Test
    fun `specification block opens then 'is true' on a later line yields True`() {
        val verdict = parser.parse(
            sequenceOf(
                "-- specification AG p",
                "  is true",
            ),
        )
        assertThat(verdict).isEqualTo(NuxmvVerdict.True)
    }

    @Test
    fun `lines outside any block do not produce a verdict`() {
        val verdict = parser.parse(
            sequenceOf(
                "Trace Type: Counterexample",
                "  noise that ends with is true",
            ),
        )
        assertThat(verdict).isNull()
    }

    @Test
    fun `empty input yields null`() {
        assertThat(parser.parse(emptySequence())).isNull()
    }

    @Test
    fun `case-insensitive match on opening line and trailing whitespace`() {
        val verdict = parser.parse(sequenceOf("-- INVARIANT (x = 5)  IS TRUE   "))
        assertThat(verdict).isEqualTo(NuxmvVerdict.True)
    }

    @Test
    fun `first decisive verdict wins`() {
        val verdict = parser.parse(
            sequenceOf(
                "-- invariant first is false",
                "-- invariant second is true",
            ),
        )
        assertThat(verdict).isEqualTo(NuxmvVerdict.False)
    }

    @Test
    fun `invert flips True and False`() {
        assertThat(NuxmvVerdict.True.invert()).isEqualTo(NuxmvVerdict.False)
        assertThat(NuxmvVerdict.False.invert()).isEqualTo(NuxmvVerdict.True)
    }

    @Test
    fun `a model nuXmv rejects names nuXmv's message`() {
        val rejection = parser.rejection(
            sequenceOf(
                "*** This is nuXmv 2.1.0 (compiled on Fri Nov 29 13:41:31 2024)",
                "file /work/model.smv: line 3: at token \"color\": syntax error",
            ),
        )

        assertThat(rejection).contains("at token \"color\": syntax error")
    }

    @Test
    fun `a command nuXmv refuses names nuXmv's message`() {
        assertThat(parser.rejection(sequenceOf("Error: the model contains infinite domain variables"))).contains("infinite domain")
        assertThat(parser.rejection(sequenceOf("Parsing error: expected an invariant"))).contains("expected an invariant")
    }

    @Test
    fun `a check that ran has no rejection`() {
        val rejection = parser.rejection(
            sequenceOf(
                "*** Copyright (c) 2014-2024, Fondazione Bruno Kessler",
                "-- no proof or counterexample found with bound 10",
            ),
        )

        assertThat(rejection).isNull()
    }

}
