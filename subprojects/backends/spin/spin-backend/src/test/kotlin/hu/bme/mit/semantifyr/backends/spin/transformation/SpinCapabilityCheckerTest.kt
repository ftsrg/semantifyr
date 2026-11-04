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
                var counter : int := 0
                var flag : bool
                var color : test::colors::Color := test::colors::Color::Red
                init { havoc(flag) }
                tran { choice { assume(counter < 3) counter := counter + 1 } or { if (flag) { color := test::colors::Color::Green } } }
                prop { AG (counter <= 3 && (if flag then true else color == test::colors::Color::Red)) }
            """,
        )

        assertThat(unsupported).isEmpty()
    }

    @Test
    fun `every unsupported construct is reported`() {
        val unsupported = unsupported(
            """
                var ratio : real := 0.5
                var anything : any := 0
                var text : string
                var counter : int := 0
                init { }
                tran { for (index in 0..2) { counter := counter + index } }
                prop { AG (if counter < 3 then 1 else 0) == 1 }
            """,
        )

        assertThat(unsupported).containsExactlyInAnyOrder(
            "variables of type real",
            "variables of type any",
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
                var counter : int := 0
                var observed : int := 0
                init { }
                tran { choice { counter := counter + 1 } or { if (counter <= 5) { assume(false) } observed := 1 } }
                prop { AG observed == 0 }
            """,
        )

        assertThat(unsupported).containsExactly("unbounded integer variables")
    }

    @Test
    fun `a bounded integer beyond 32 bits is reported`() {
        val unsupported = unsupported(
            """
                var counter : int := 1
                init { }
                tran { if (counter < 100000) { counter := counter * 100000 } }
                prop { AG counter > 0 }
            """,
        )

        assertThat(unsupported).containsExactly("integer variables beyond the 32-bit range")
    }

    @Test
    fun `arithmetic beyond 32 bits over bounded integers is reported`() {
        val unsupported = unsupported(
            """
                var counter : int := 0
                var scaled : int := 0
                init { }
                tran { if (counter < 5000) { counter := counter + 1 } scaled := (counter * 1000000) / 1000000 }
                prop { AG scaled <= 5000 }
            """,
        )

        assertThat(unsupported).containsExactly("arithmetic that may leave the 32-bit range")
    }

    @Test
    fun `an integer chosen among havoc candidates is bounded by them`() {
        val unsupported = unsupported(
            """
                var counter : int
                init { }
                tran { havoc(counter) }
                prop { AG counter < 5 }
            """,
        )

        assertThat(unsupported).isEmpty()
    }
}
