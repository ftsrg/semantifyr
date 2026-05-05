/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.spin.trace

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SpinLogVerdictParserTest {

    private val parser = SpinLogVerdictParser()

    @Test
    fun `clean run with errors zero yields True`() {
        val verdict = parser.parse(
            sequenceOf(
                "(Spin Version 6.5.2)",
                "errors: 0",
            ),
        )
        assertThat(verdict).isEqualTo(SpinVerdict.True)
    }

    @Test
    fun `LTL violated line yields False`() {
        val verdict = parser.parse(
            sequenceOf(
                "  ltl claim violated at depth 5",
                "errors: 1",
            ),
        )
        assertThat(verdict).isEqualTo(SpinVerdict.False)
    }

    @Test
    fun `acceptance cycle yields False even with errors zero earlier`() {
        val verdict = parser.parse(
            sequenceOf(
                "errors: 0",
                "acceptance cycle (at depth 7)",
            ),
        )
        assertThat(verdict).isEqualTo(SpinVerdict.False)
    }

    @Test
    fun `assertion violated yields False`() {
        val verdict = parser.parse(sequenceOf("assertion violated at line 12"))
        assertThat(verdict).isEqualTo(SpinVerdict.False)
    }

    @Test
    fun `empty input yields null`() {
        assertThat(parser.parse(emptySequence())).isNull()
    }

    @Test
    fun `unrelated lines yield null`() {
        assertThat(parser.parse(sequenceOf("hint: 'pan' does this", "Trace ended"))).isNull()
    }

    @Test
    fun `failure outweighs success when both signals are present`() {
        val verdict = parser.parse(
            sequenceOf(
                "errors: 0",
                "violated",
            ),
        )
        assertThat(verdict).isEqualTo(SpinVerdict.False)
    }

    @Test
    fun `invert flips True and False`() {
        assertThat(SpinVerdict.True.invert()).isEqualTo(SpinVerdict.False)
        assertThat(SpinVerdict.False.invert()).isEqualTo(SpinVerdict.True)
    }
}
