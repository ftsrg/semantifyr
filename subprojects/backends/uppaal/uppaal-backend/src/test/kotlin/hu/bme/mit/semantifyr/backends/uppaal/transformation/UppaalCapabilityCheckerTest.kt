/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.uppaal.transformation

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class UppaalCapabilityCheckerTest : UppaalTransformationTestBase() {

    private val colors = """
        package test::colors
        enum Color { Red, Green }
    """

    private fun unsupported(body: String): List<String> {
        val inlinedOxsts = parse("inlined oxsts of semantifyr::Anything\n${body.trimIndent()}", colors)
        return capabilityChecker().check(inlinedOxsts).map {
            it.description
        }
    }

    @Test
    fun `a model of supported constructs passes`() {
        val unsupported = unsupported(
            """
                var x : int := 0
                var b : bool
                var c : test::colors::Color := test::colors::Color::Red
                init { havoc(b) }
                tran { choice { assume(x < 3) x := x + 1 } or { if (b) { c := test::colors::Color::Green } } }
                prop { AG (x <= 3 && (if b then true else c == test::colors::Color::Red)) }
            """,
        )

        assertThat(unsupported).isEmpty()
    }

    @Test
    fun `every unsupported construct is reported`() {
        val unsupported = unsupported(
            """
                var r : real := 0.5
                @semantifyr::Clock var c : int := 0
                @semantifyr::Clock var e : test::colors::Color := test::colors::Color::Red
                var x : int := 0
                init { }
                tran { c := 0 c := 5 havoc(c) for (i in 0 .. 2) { x := x + i } }
                prop { AG true }
            """,
        )

        assertThat(unsupported).containsExactlyInAnyOrder(
            "variables of type real",
            "@Clock on variables that are not data-typed",
            "clock resets to values other than 0",
            "havoc on clocks",
            "for-operations",
        )
    }
}
