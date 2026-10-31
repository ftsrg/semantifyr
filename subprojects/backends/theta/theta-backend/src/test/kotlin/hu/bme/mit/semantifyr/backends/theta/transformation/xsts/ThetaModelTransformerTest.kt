/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.theta.transformation.xsts

import hu.bme.mit.semantifyr.backend.BackendUnsupportedException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ThetaModelTransformerTest : ThetaTransformationTestBase() {

    private val colors = """
        package test::colors
        enum Color { Red, Green }
    """

    private suspend fun xsts(body: String): String {
        return withTransformer {
            serialize(it.transform(parse("inlined oxsts of semantifyr::Anything\n${body.trimIndent()}", colors), testXstsUri))
        }
    }

    private suspend fun xstsLines(body: String): List<String> {
        return xsts(body).lines().map {
            it.trim()
        }.filter {
            it.isNotEmpty()
        }
    }

    @Test
    suspend fun `a model renders variables, the transitions and the property`() {
        val xsts = xsts(
            """
                var x : int := 0
                init { x := 1 }
                tran { x := x + 1 }
                prop { AG x < 3 }
            """,
        )

        assertThat(xsts).isEqualTo(
            """
                |
                |var x: integer = 0
                |trans {
                |	x := x + 1;
                |}
                |init {
                |	x := 1;
                |}
                |env {
                |}
                |prop { x < 3
                |}
                |
            """.trimMargin(),
        )
    }

    @Test
    suspend fun `variables keep their types and initializers`() {
        val lines = xstsLines(
            """
                var x : int
                var b : bool := true
                var c : test::colors::Color := test::colors::Color::Green
                init { }
                tran { }
                prop { AG true }
            """,
        )

        assertThat(lines).containsSequence("type Color : { Red, Green", "}", "var x: integer", "var b: boolean = true", "var c: Color = Color.Green")
    }

    @Test
    suspend fun `assumptions, choices and ifs map to their XSTS forms`() {
        val lines = xstsLines(
            """
                var x : int := 0
                init { }
                tran {
                    assume(x < 3)
                    choice { x := 1 } or { }
                    if (x < 3) { x := 1 } else { x := 2 }
                    if (x > 3) { x := 3 }
                }
                prop { AG true }
            """,
        )

        assertThat(lines).containsSequence(
            "trans {",
            "assume x < 3;",
            "choice {",
            "x := 1;",
            "} or {",
            "}",
            "if ( x < 3 ) {",
            "x := 1;",
            "} else {",
            "x := 2;",
            "}",
            "if ( x > 3 ) {",
            "x := 3;",
            "}",
            "}",
        )
    }

    @Test
    suspend fun `havocs are native`() {
        val lines = xstsLines(
            """
                var x : int := 0
                var b : bool := false
                var c : test::colors::Color := test::colors::Color::Red
                init { }
                tran { havoc(x) havoc(b) havoc(c) }
                prop { AG true }
            """,
        )

        assertThat(lines).containsSequence("havoc x;", "havoc b;", "havoc c;")
    }

    @Test
    suspend fun `local variables are declared in the step`() {
        val lines = xstsLines(
            """
                var x : int := 0
                init { }
                tran { var d : int := 2 var e : int x := x + d + e }
                prop { AG true }
            """,
        )

        assertThat(lines).containsSequence("local var d : integer = 2;", "local var e : integer;", "x := x + d + e;")
    }

    @Test
    suspend fun `operators keep the meaning of the source grouping`() {
        val lines = xstsLines(
            """
                var x : int := 0
                var a : bool := false
                var b : bool := true
                init { }
                tran {
                    x := ((x + 1) - (2 * x)) / 3
                    x := x - (1 - x)
                    a := (a && b) || !(a ^^ b)
                    a := a && (b || a)
                    assume(x < 1 && x <= 2 && x > -3 && x >= -4 && x == 0 && x != 5)
                    x := if x < 3 then x + 1 else 0
                }
                prop { AG x == -(x + 1) }
            """,
        )

        assertThat(lines).containsSequence(
            "x := ( x + 1 - 2 * x ) / 3;",
            "x := x - ( 1 - x );",
            "a := a && b || !( a xor b );",
            "a := a && ( b || a );",
            "assume x < 1 && x <= 2 && x > - 3 && x >= - 4 && x == 0 && x != 5;",
            "x := if x < 3 then x + 1 else 0;",
        )
        assertThat(lines).contains("prop { x == - ( x + 1 )")
    }

    @Test
    suspend fun `several branches become an or of sequences`() {
        val lines = xstsLines(
            """
                var x : int := 0
                init { }
                tran { x := 1 } or { x := 2 }
                prop { AG true }
            """,
        )

        assertThat(lines).containsSequence("trans {", "x := 1;", "} or {", "x := 2;", "}")
    }

    @Test
    suspend fun `an AG property is the invariant itself`() {
        val lines = xstsLines(
            """
                var x : int := 0
                init { }
                tran { }
                prop { AG x == 5 }
            """,
        )

        assertThat(lines).contains("prop { x == 5")
    }

    @Test
    suspend fun `an EF property is the negated invariant`() {
        val lines = xstsLines(
            """
                var c : test::colors::Color := test::colors::Color::Red
                init { }
                tran { }
                prop { EF c == test::colors::Color::Green }
            """,
        )

        assertThat(lines).contains("prop { !( c == Color.Green )")
    }

    @Test
    suspend fun `a real literal is not supported`() {
        assertThrows<BackendUnsupportedException> {
            xsts(
                """
                    var b : bool := false
                    init { }
                    tran { b := 1.5 > 1.0 }
                    prop { AG true }
                """,
            )
        }
    }
}
