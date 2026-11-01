/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.spin.transformation

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SpinCapabilityCheckerTest : SpinTransformationTestBase() {

    private val colors = """
        package test::colors
        enum Color { Red, Green }
    """

    private fun unsupported(body: String): List<String> {
        val inlinedOxsts = parse("inlined oxsts of semantifyr::Anything\n${body.trimIndent()}", colors)
        return withCapabilityChecker {
            it.check(inlinedOxsts)
        }.map {
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
                var s : string
                var x : int := 0
                init { }
                tran { for (i in 0 .. 2) { x := x + i } }
                prop { AG (if x < 3 then 1 else 0) == 1 }
            """,
        )

        assertThat(unsupported).containsExactlyInAnyOrder(
            "variables of type real",
            "real literals",
            "variables of type string",
            "for-operations",
            "non-boolean if-then-else inside a property",
            "unbounded integer variables",
        )
    }

    @Test
    fun `an unbounded integer is reported only when the property depends on it`() {
        val unsupported = unsupported(
            """
                var counted : int := 0
                var ignored : int := 0
                init { }
                tran { counted := counted + 1 ignored := ignored + 1 }
                prop { AG counted >= 0 }
            """,
        )

        assertThat(unsupported).containsExactly("unbounded integer variables")
    }

    @Test
    fun `an unbounded integer that guards an assumption is reported`() {
        val unsupported = unsupported(
            """
                var x : int := 0
                var y : int := 0
                init { }
                tran { choice { x := x + 1 } or { if (x <= 5) { assume(false) } y := 1 } }
                prop { AG y == 0 }
            """,
        )

        assertThat(unsupported).containsExactly("unbounded integer variables")
    }

    @Test
    fun `a bounded integer beyond 32 bits is reported`() {
        val unsupported = unsupported(
            """
                var x : int := 1
                init { }
                tran { if (x < 100000) { x := x * 100000 } }
                prop { AG x > 0 }
            """,
        )

        assertThat(unsupported).containsExactly("integer variables beyond the 32-bit range")
    }

    @Test
    fun `arithmetic beyond 32 bits over bounded integers is reported`() {
        val unsupported = unsupported(
            """
                var x : int := 0
                var y : int := 0
                init { }
                tran { if (x < 5000) { x := x + 1 } y := (x * 1000000) / 1000000 }
                prop { AG y <= 5000 }
            """,
        )

        assertThat(unsupported).containsExactly("arithmetic that may leave the 32-bit range")
    }

    @Test
    fun `an integer chosen among havoc candidates is bounded by them`() {
        val unsupported = unsupported(
            """
                var x : int
                init { }
                tran { havoc(x) }
                prop { AG x < 5 }
            """,
        )

        assertThat(unsupported).isEmpty()
    }
}
