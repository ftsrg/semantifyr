/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.theta.trace

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ThetaLogVerdictParserTest {

    private val parser = ThetaLogVerdictParser()

    @Test
    fun `a safe result yields Safe`() {
        val verdict = parser.parse(
            sequenceOf(
                "Refining abstraction done, result: (RefinerResult Spurious)",
                "(SafetyResult Safe)",
            ),
        )

        assertThat(verdict).isEqualTo(ThetaVerdict.Safe)
    }

    @Test
    fun `an unsafe result with a trace length yields Unsafe`() {
        val verdict = parser.parse(
            sequenceOf(
                "|  |  Checking trace...done, result: (ExprTraceStatus Feasible)",
                "Refining abstraction done, result: (RefinerResult Unsafe)",
                "(SafetyResult Unsafe Trace length: 5)",
            ),
        )

        assertThat(verdict).isEqualTo(ThetaVerdict.Unsafe)
    }

    @Test
    fun `the first result wins`() {
        val verdict = parser.parse(
            sequenceOf(
                "(SafetyResult Unsafe Trace length: 3)",
                "(SafetyResult Safe)",
            ),
        )

        assertThat(verdict).isEqualTo(ThetaVerdict.Unsafe)
    }

    @Test
    fun `progress lines without a result yield null`() {
        val verdict = parser.parse(
            sequenceOf(
                "Refining abstraction done, result: (RefinerResult Unsafe)",
                "! Precision did NOT change in this iteration",
            ),
        )

        assertThat(verdict).isNull()
    }

    @Test
    fun `a crash yields null`() {
        val verdict = parser.parse(
            sequenceOf(
                "Exception of type OutOfMemoryError occurred",
                "Message:",
                "Java heap space",
            ),
        )

        assertThat(verdict).isNull()
    }

    @Test
    fun `empty input yields null`() {
        assertThat(parser.parse(emptySequence())).isNull()
    }
}
