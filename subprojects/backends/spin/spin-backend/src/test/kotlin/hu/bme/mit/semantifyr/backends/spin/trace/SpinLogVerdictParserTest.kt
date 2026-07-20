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
    fun `clean exhaustive run with errors zero yields True`() {
        val verdict = parser.parse(
            sequenceOf(
                "(Spin Version 6.5.2)",
                "Full statespace search for:",
                "State-vector 28 byte, depth reached 25, errors: 0",
            ),
        )
        assertThat(verdict).isEqualTo(SpinVerdict.True)
    }

    @Test
    fun `errors zero without an exhaustive search header yields null`() {
        val verdict = parser.parse(
            sequenceOf(
                "(Spin Version 6.5.2)",
                "errors: 0",
            ),
        )
        assertThat(verdict)
            .describedAs("Success requires positive evidence of an exhaustive search")
            .isNull()
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
                "Full statespace search for:",
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

    @Test
    fun `depth-truncated search with errors zero yields null`() {
        val verdict = parser.parse(
            sequenceOf(
                "error: max search depth too small",
                "(Spin Version 6.5.2 -- 6 December 2019)",
                "Full statespace search for:",
                "State-vector 116 byte, depth reached 9999, errors: 0",
            ),
        )
        assertThat(verdict)
            .describedAs("A depth-truncated search proves nothing, errors: 0 must not count as success")
            .isNull()
    }

    @Test
    fun `depth-truncated search with a violation still yields False`() {
        val verdict = parser.parse(
            sequenceOf(
                "error: max search depth too small",
                "pan:1: assertion violated !(...) (at depth 18690)",
                "State-vector 116 byte, depth reached 18690, errors: 1",
            ),
        )
        assertThat(verdict).isEqualTo(SpinVerdict.False)
    }

    @Test
    fun `bitstate search with errors zero yields null`() {
        val verdict = parser.parse(
            sequenceOf(
                "Bit statespace search for:",
                "State-vector 28 byte, depth reached 25, errors: 0",
                "hash factor: 44398.9 (best if > 100.)",
            ),
        )
        assertThat(verdict)
            .describedAs("Lossy bitstate hashing cannot prove safety")
            .isNull()
    }

    @Test
    fun `hash-compact search with errors zero yields null`() {
        val verdict = parser.parse(
            sequenceOf(
                "Hash-Compact 4 search for:",
                "State-vector 116 byte, depth reached 120, errors: 0",
            ),
        )
        assertThat(verdict).isNull()
    }

    @Test
    fun `bitstate search with a violation yields False`() {
        val verdict = parser.parse(
            sequenceOf(
                "Bit statespace search for:",
                "pan:1: assertion violated !(...) (at depth 18690)",
                "State-vector 116 byte, depth reached 18690, errors: 1",
            ),
        )
        assertThat(verdict).isEqualTo(SpinVerdict.False)
    }

    @Test
    fun `aborted search with errors zero yields null`() {
        val verdict = parser.parse(
            sequenceOf(
                "Warning: Search not completed",
                "Full statespace search for:",
                "State-vector 116 byte, depth reached 4000, errors: 0",
            ),
        )
        assertThat(verdict).isNull()
    }

    @Test
    fun `out of memory run with errors zero yields null`() {
        val verdict = parser.parse(
            sequenceOf(
                "pan: out of memory",
                "Full statespace search for:",
                "State-vector 116 byte, depth reached 4000, errors: 0",
            ),
        )
        assertThat(verdict).isNull()
    }

    @Test
    fun `safety-mode accept-label warning does not disqualify a clean run`() {
        val verdict = parser.parse(
            sequenceOf(
                "warning: never claim + accept labels requires -a flag to fully verify",
                "Full statespace search for:",
                "State-vector 116 byte, depth reached 25, errors: 0",
            ),
        )
        assertThat(verdict)
            .describedAs("The -a warning only concerns liveness, our claims are safety-shaped")
            .isEqualTo(SpinVerdict.True)
    }

    @Test
    fun `summary exposes a refused non-exhaustive success`() {
        val summary = parser.summarize(
            sequenceOf(
                "error: max search depth too small",
                "Full statespace search for:",
                "State-vector 116 byte, depth reached 9999, errors: 0",
            ),
        )
        assertThat(summary.toVerdict()).isNull()
        assertThat(summary.reportedNoErrors).isTrue
        assertThat(summary.executionProblem).isTrue
    }
}
