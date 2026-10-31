/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.nuxmv.transformation

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class NuxmvModelTransformerTest : NuxmvTransformationTestBase() {

    private val colors = """
        package test::colors
        enum Color { Red, Green }
    """

    private suspend fun transform(body: String): NuxmvArtifacts {
        return withTransformer {
            it.transform(parse("inlined oxsts of semantifyr::Anything\n${body.trimIndent()}", colors))
        }
    }

    private suspend fun smvLines(body: String): List<String> {
        return transform(body).smv.lines().map {
            it.trim()
        }
    }

    @Test
    suspend fun `a model renders the variables, their seeds, the init and the transition relation`() {
        val artifacts = transform(
            """
                var x : int := 0
                init { }
                tran { x := x + 1 }
                prop { AG x < 3 }
            """,
        )

        assertThat(artifacts.smv).isEqualTo(
            """
                |MODULE main
                |VAR
                |    x : integer;
                |    x__init_seed_0 : integer;
                |    x__tran_b0_1 : integer;
                |INIT
                |    (
                |          (x__init_seed_0 = 0)
                |        & (x = x__init_seed_0)
                |    )
                |TRANS
                |    (
                |          (x__tran_b0_1 = (x + 1))
                |        & (next(x) = x__tran_b0_1)
                |    )
                |
            """.trimMargin(),
        )
        assertThat(artifacts.property.invariant).isEqualTo("(x < 3)")
    }

    @Test
    suspend fun `a variable without an initializer starts unconstrained`() {
        val lines = smvLines(
            """
                var x : int
                init { }
                tran { }
                prop { AG x == 0 }
            """,
        )

        assertThat(lines).containsSequence("INIT", "(", "(x = x__init_seed_0)", ")")
    }

    @Test
    suspend fun `boolean and enum variables get their domains`() {
        val lines = smvLines(
            """
                var b : bool := true
                var c : test::colors::Color := test::colors::Color::Green
                init { }
                tran { }
                prop { AG c == test::colors::Color::Red }
            """,
        )

        assertThat(lines).contains("b : boolean;", "c : { Red, Green };", "(b__init_seed_0 = TRUE)", "& (c__init_seed_0 = Green)")
    }

    @Test
    suspend fun `an assumption constrains the step`() {
        val lines = smvLines(
            """
                var x : int := 0
                init { }
                tran { assume(x < 3) }
                prop { AG true }
            """,
        )

        assertThat(lines).containsSequence("TRANS", "(", "((x < 3))", "& (next(x) = x)", ")")
    }

    @Test
    suspend fun `a choice selects its branch with an input variable`() {
        val lines = smvLines(
            """
                var x : int := 0
                init { }
                tran { choice { x := 1 } or { } }
                prop { AG true }
            """,
        )

        assertThat(lines).containsSequence("IVAR", "nondet_tran_0 : 0..1;")
        assertThat(lines).containsSequence(
            "(case nondet_tran_0 = 0 : (x__tran_b0_1 = 1); nondet_tran_0 = 1 : TRUE; esac)",
            "& (x__tran_b0_2 = case nondet_tran_0 = 0 : x__tran_b0_1; nondet_tran_0 = 1 : x; esac)",
            "& (next(x) = x__tran_b0_2)",
        )
    }

    @Test
    suspend fun `a choice in init selects its branch with a frozen variable`() {
        val lines = smvLines(
            """
                var x : int := 0
                init { choice { x := 1 } or { x := 2 } }
                tran { }
                prop { AG true }
            """,
        )

        assertThat(lines).containsSequence("FROZENVAR", "nondet_init_0 : 0..1;")
        assertThat(lines).doesNotContain("IVAR")
    }

    @Test
    suspend fun `an if merges both branches by its guard`() {
        val lines = smvLines(
            """
                var x : int := 0
                init { }
                tran { if (x < 3) { x := 1 } else { x := 2 } }
                prop { AG true }
            """,
        )

        assertThat(lines).containsSequence(
            "(case (x < 3) : (x__tran_b0_1 = 1); TRUE : (x__tran_b0_2 = 2); esac)",
            "& (x__tran_b0_3 = case (x < 3) : x__tran_b0_1; TRUE : x__tran_b0_2; esac)",
            "& (next(x) = x__tran_b0_3)",
        )
    }

    @Test
    suspend fun `an if without an else keeps the value on the else branch`() {
        val lines = smvLines(
            """
                var x : int := 0
                init { }
                tran { if (x < 3) { x := 1 } }
                prop { AG true }
            """,
        )

        assertThat(lines).containsSequence(
            "(case (x < 3) : (x__tran_b0_1 = 1); TRUE : TRUE; esac)",
            "& (x__tran_b0_2 = case (x < 3) : x__tran_b0_1; TRUE : x; esac)",
        )
    }

    @Test
    suspend fun `a havoc binds the next value to an unconstrained prime`() {
        val lines = smvLines(
            """
                var x : int := 0
                var b : bool := false
                var c : test::colors::Color := test::colors::Color::Red
                init { }
                tran { havoc(x) havoc(b) havoc(c) }
                prop { AG true }
            """,
        )

        assertThat(lines).containsSequence(
            "TRANS",
            "(",
            "(next(x) = x__tran_b0_1)",
            "& (next(b) = b__tran_b0_1)",
            "& (next(c) = c__tran_b0_1)",
            ")",
        )
    }

    @Test
    suspend fun `a local variable becomes a primed value of the step`() {
        val smv = transform(
            """
                var x : int := 0
                init { }
                tran { var d : int := 2 x := x + d }
                prop { AG true }
            """,
        ).smv

        val local = Regex("\\((d_\\d+)__tran_b0_1 = 2\\)").find(smv)
        assertThat(local).isNotNull()
        assertThat(smv).contains("(x__tran_b0_1 = (x + ${local!!.groupValues[1]}__tran_b0_1))")
    }

    @Test
    suspend fun `arithmetic, boolean and comparison operators render in SMV syntax`() {
        val lines = smvLines(
            """
                var x : int := 0
                var a : bool := false
                var b : bool := true
                init { }
                tran {
                    x := ((x + 1) - (2 * x)) / 3
                    a := (a && b) || !(a ^^ b)
                    assume(x < 1 && x <= 2 && x > -3 && x >= -4 && x == 0 && x != 5)
                }
                prop { AG x == -x }
            """,
        )

        assertThat(lines).contains(
            "(x__tran_b0_1 = (((x + 1) - (2 * x)) / 3))",
            "& (a__tran_b0_1 = ((a & b) | !((a xor b))))",
            "& (((((((x__tran_b0_1 < 1) & (x__tran_b0_1 <= 2)) & (x__tran_b0_1 > (-3))) & (x__tran_b0_1 >= (-4))) & (x__tran_b0_1 = 0)) & (x__tran_b0_1 != 5)))",
        )
    }

    @Test
    suspend fun `an if-then-else expression is a case expression`() {
        val artifacts = transform(
            """
                var x : int := 0
                init { }
                tran { x := if x < 3 then x + 1 else 0 }
                prop { AG (if x < 3 then x == 0 else x == 1) }
            """,
        )

        assertThat(artifacts.smv).contains("(x__tran_b0_1 = case (x < 3) : (x + 1); TRUE : 0; esac)")
        assertThat(artifacts.property.invariant).isEqualTo("case (x < 3) : (x = 0); TRUE : (x = 1); esac")
    }

    @Test
    suspend fun `several transition branches become a disjunction`() {
        val lines = smvLines(
            """
                var x : int := 0
                init { }
                tran { x := 1 } or { x := 2 }
                prop { AG true }
            """,
        )

        assertThat(lines).containsSequence(
            "TRANS",
            "(",
            "(x__tran_b0_1 = 1)",
            "& (next(x) = x__tran_b0_1)",
            ")",
            "| (",
            "(x__tran_b1_1 = 2)",
            "& (next(x) = x__tran_b1_1)",
            ")",
        )
    }

    @Test
    suspend fun `an AG property keeps the verdict and the body`() {
        val property = transform(
            """
                var x : int := 0
                init { }
                tran { }
                prop { AG x == 5 }
            """,
        ).property

        assertThat(property.invertVerdict).isFalse()
        assertThat(property.invariant).isEqualTo("(x = 5)")
    }

    @Test
    suspend fun `an EF property is checked as the negated invariant with an inverted verdict`() {
        val property = transform(
            """
                var x : int := 0
                init { }
                tran { }
                prop { EF x == 5 }
            """,
        ).property

        assertThat(property.invertVerdict).isTrue()
        assertThat(property.invariant).isEqualTo("!((x = 5))")
    }
}
