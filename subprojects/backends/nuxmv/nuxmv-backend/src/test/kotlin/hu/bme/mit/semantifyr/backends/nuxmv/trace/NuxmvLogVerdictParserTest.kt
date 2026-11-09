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
                "",
                "file /work/model.smv: line 6: at token \"TRANS\": syntax error",
                "aborting 'source commands.cmd'",
            ),
        )

        assertThat(rejection).isEqualTo("file /work/model.smv: line 6: at token \"TRANS\": syntax error")
    }

    @Test
    fun `a property nuXmv refuses names nuXmv's message`() {
        val rejection = parser.rejection(
            sequenceOf(
                "TYPE ERROR file /work/model.smv: line 6 : undefined identifier 'z'",
                "ERROR: Property \"z < 3 \" is not correct or not well typed.",
                "aborting 'source commands.cmd'",
            ),
        )

        assertThat(rejection).contains("undefined identifier 'z'", "is not correct or not well typed")
    }

    @Test
    fun `a BDD check of an unbounded integer names nuXmv's message`() {
        val rejection = parser.rejection(sequenceOf("file /work/model.smv: line 6: Impossible to build a BDD FSM with infinite precision variables"))

        assertThat(rejection).contains("Impossible to build a BDD FSM with infinite precision variables")
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
