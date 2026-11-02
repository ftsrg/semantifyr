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

    private fun unsupported(
        body: String,
        checkerClass: Class<out UppaalCapabilityChecker> = UppaalCapabilityChecker::class.java,
    ): List<String> {
        val inlinedOxsts = parse("inlined oxsts of semantifyr::Anything\n${body.trimIndent()}", colors)
        return withCapabilityChecker(checkerClass) {
            it.check(inlinedOxsts)
        }.map {
            it.description
        }
    }

    private fun overApproximationUnsupported(body: String): List<String> {
        return unsupported(body, UppaalOverApproximationCapabilityChecker::class.java)
    }

    @Test
    fun `the over-approximation refuses a havoced integer read other than in comparisons with literals`() {
        val havoc = """
            var x : int := 0
            var y : int := 0
            init { }
            tran { havoc(x) y := x + 1 }
            prop { AG y < 5 }
        """
        val uninitialized = """
            var x : int
            init { }
            tran { }
            prop { AG x * 2 < 5 }
        """
        val local = """
            var y : int := 0
            init { }
            tran { var l : int y := l }
            prop { AG y < 5 }
        """

        assertThat(overApproximationUnsupported(havoc)).containsExactly(UppaalOverApproximationCapabilityChecker.UNDER_APPROXIMATED_HAVOC)
        assertThat(overApproximationUnsupported(uninitialized)).contains(UppaalOverApproximationCapabilityChecker.UNDER_APPROXIMATED_HAVOC)
        assertThat(overApproximationUnsupported(local)).containsExactly(UppaalOverApproximationCapabilityChecker.UNDER_APPROXIMATED_HAVOC)
        assertThat(unsupported(havoc)).isEmpty()
    }

    @Test
    fun `the over-approximation accepts havoced integers compared only with literals and havoced booleans`() {
        val unsupported = overApproximationUnsupported(
            """
                var x : int
                var b : bool
                init { }
                tran { havoc(x) havoc(b) }
                prop { AG (x == 3 || x < 0 || b) }
            """,
        )

        assertThat(unsupported).isEmpty()
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
                tran { c := 0 c := 5 havoc(c) for (i in 0..2) { x := x + i } }
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

    @Test
    fun `arithmetic over an unbounded integer is reported only when the property depends on it`() {
        val unsupported = unsupported(
            """
                var counted : int := 1
                var ignored : int := 1
                init { }
                tran { counted := counted * 3 ignored := ignored * 3 }
                prop { AG counted > 0 }
            """,
        )

        assertThat(unsupported).containsExactly("arithmetic that may leave the 32-bit range")
    }

    @Test
    fun `incrementing or doubling an unbounded integer stays within 32 bits under the 31-bit range`() {
        val unsupported = unsupported(
            """
                var counted : int := 0
                var doubled : int := 1
                init { }
                tran { counted := counted + 1 doubled := doubled * 2 }
                prop { AG (counted >= 0 && doubled > 0) }
            """,
        )

        assertThat(unsupported).isEmpty()
    }

    @Test
    fun `an unbounded integer without arithmetic that may leave 32 bits is not reported`() {
        val unsupported = unsupported(
            """
                var x : int := 0
                var y : int := 0
                init { }
                tran { if (x < 100) { x := x + 1 } y := y / 2 + x }
                prop { AG y >= 0 }
            """,
        )

        assertThat(unsupported).isEmpty()
    }

    @Test
    fun `arithmetic is reported only when it may leave 32 bits under the declared ranges`() {
        val unsupported = unsupported(
            """
                var x : int := 1
                var doubled : int := 2
                var scaled : int := 100000
                init { }
                tran { if (x < 32767) { x := x + 1 } doubled := x * 2 scaled := x * 100000 }
                prop { AG (doubled > 0 && scaled > 0) }
            """,
        )

        assertThat(unsupported).containsExactly("arithmetic that may leave the 32-bit range")
    }
}
