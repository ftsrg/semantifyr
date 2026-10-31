/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.spin.transformation

import hu.bme.mit.semantifyr.backend.BackendUnsupportedException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SpinModelTransformerTest : SpinTransformationTestBase() {

    private val colors = """
        package test::colors
        enum Color { Red, Green }
    """

    private suspend fun transform(body: String): SpinArtifacts {
        return withTransformer {
            it.transform(parse("inlined oxsts of semantifyr::Anything\n${body.trimIndent()}", colors))
        }
    }

    private suspend fun promelaLines(body: String): List<String> {
        return transform(body).promela.lines().map {
            it.trim()
        }
    }

    @Test
    suspend fun `a model renders globals, the stable flag, the init block, the transition loop and the claim`() {
        val artifacts = transform(
            """
                var x : int := 0
                init { }
                tran { x := x + 1 }
                prop { AG x < 3 }
            """,
        )

        assertThat(artifacts.promela).isEqualTo(
            """
                |int x = 0;
                |
                |bool $SPIN_STABLE_FLAG = false;
                |
                |init {
                |    atomic {
                |        $SPIN_STABLE_FLAG = false;
                |        skip;
                |        $SPIN_STABLE_FLAG = true;
                |    }
                |    do
                |    :: atomic {
                |        $SPIN_STABLE_FLAG = false;
                |        x = (x + 1);
                |        $SPIN_STABLE_FLAG = true;
                |    }
                |    od;
                |}
                |
                |ltl p { [] (!$SPIN_STABLE_FLAG || ((x < 3))) }
                |
            """.trimMargin(),
        )
    }

    @Test
    suspend fun `a variable with an initializer is declared with it`() {
        val lines = promelaLines(
            """
                var x : int := 2
                var b : bool := true
                init { }
                tran { }
                prop { AG true }
            """,
        )

        assertThat(lines).containsSequence("int x = 2;", "bool b = true;")
    }

    @Test
    suspend fun `variables without an initializer start at any value of their type`() {
        val lines = promelaLines(
            """
                var x : int
                var b : bool
                var c : test::colors::Color
                init { x := x + 1 }
                tran { }
                prop { AG x < 5 }
            """,
        )

        assertThat(lines).containsSequence(
            "init {",
            "atomic {",
            "if",
            ":: x = 1",
            ":: x = 4",
            ":: x = 5",
            "fi;",
            "if",
            ":: b = false",
            ":: b = true",
            "fi;",
            "if",
            ":: c = Red",
            ":: c = Green",
            "fi;",
            "}",
            "atomic {",
            "$SPIN_STABLE_FLAG = false;",
            "x = (x + 1);",
            "$SPIN_STABLE_FLAG = true;",
            "}",
        )
    }

    @Test
    suspend fun `variables with an initializer are not chosen again`() {
        val lines = promelaLines(
            """
                var x : int := 0
                init { }
                tran { }
                prop { AG x < 5 }
            """,
        )

        assertThat(lines).containsSequence("init {", "atomic {", "$SPIN_STABLE_FLAG = false;", "skip;")
    }

    @Test
    suspend fun `a local variable without an initializer starts at any value in every step`() {
        val promela = transform(
            """
                var x : int := 0
                init { }
                tran { var d : int x := d }
                prop { AG x < 3 }
            """,
        ).promela

        val local = Regex("int (d_\\d+);").find(promela)!!.groupValues[1]
        assertThat(promela.lines().map { it.trim() }).containsSequence(
            "int $local;",
            "if",
            ":: $local = 0",
            ":: $local = 3",
            "fi;",
            "x = $local;",
        )
    }

    @Test
    suspend fun `an enum variable declares its mtype`() {
        val lines = promelaLines(
            """
                var c : test::colors::Color := test::colors::Color::Green
                init { }
                tran { }
                prop { AG c == test::colors::Color::Red }
            """,
        )

        assertThat(lines).contains("mtype:Color = { Red, Green };", "mtype:Color c = Green;")
        assertThat(lines).contains("ltl p { [] (!$SPIN_STABLE_FLAG || ((c == Red))) }")
    }

    @Test
    suspend fun `an assumption is a blocking guard`() {
        val lines = promelaLines(
            """
                var x : int := 0
                init { }
                tran { assume(x < 3) }
                prop { AG true }
            """,
        )

        assertThat(lines).containsSequence("$SPIN_STABLE_FLAG = false;", "((x < 3));", "$SPIN_STABLE_FLAG = true;")
    }

    @Test
    suspend fun `a choice renders an atomic option per branch`() {
        val lines = promelaLines(
            """
                var x : int := 0
                init { }
                tran { choice { x := 1 } or { } }
                prop { AG true }
            """,
        )

        assertThat(lines).containsSequence("if", ":: atomic {", "x = 1;", "}", ":: skip", "fi;")
    }

    @Test
    suspend fun `an if with an else renders both guarded options`() {
        val lines = promelaLines(
            """
                var x : int := 0
                init { }
                tran { if (x < 3) { x := 1 } else { x := 2 } }
                prop { AG true }
            """,
        )

        assertThat(lines).containsSequence("if", ":: ((x < 3)) ->", "x = 1;", ":: else ->", "x = 2;", "fi;")
    }

    @Test
    suspend fun `an if without an else skips on the else option`() {
        val lines = promelaLines(
            """
                var x : int := 0
                init { }
                tran { if (x < 3) { x := 1 } }
                prop { AG true }
            """,
        )

        assertThat(lines).containsSequence("if", ":: ((x < 3)) ->", "x = 1;", ":: else -> skip", "fi;")
    }

    @Test
    suspend fun `an integer havoc chooses among the values the model compares against`() {
        val lines = promelaLines(
            """
                var x : int := 0
                init { }
                tran { havoc(x) }
                prop { AG x < 5 }
            """,
        )

        assertThat(lines).containsSequence("if", ":: x = 0", ":: x = 4", ":: x = 5", "fi;")
    }

    @Test
    suspend fun `a boolean havoc chooses both values`() {
        val lines = promelaLines(
            """
                var b : bool := false
                init { }
                tran { havoc(b) }
                prop { AG true }
            """,
        )

        assertThat(lines).containsSequence("if", ":: b = false", ":: b = true", "fi;")
    }

    @Test
    suspend fun `an enum havoc chooses every literal`() {
        val lines = promelaLines(
            """
                var c : test::colors::Color := test::colors::Color::Red
                init { }
                tran { havoc(c) }
                prop { AG true }
            """,
        )

        assertThat(lines).containsSequence("if", ":: c = Red", ":: c = Green", "fi;")
    }

    @Test
    suspend fun `a local variable is declared inside the step`() {
        val promela = transform(
            """
                var x : int := 0
                init { }
                tran { var d : int := 2 x := x + d }
                prop { AG true }
            """,
        ).promela

        val local = Regex("int (d_\\d+) = 2;").find(promela)
        assertThat(local).isNotNull()
        assertThat(promela).contains("x = (x + ${local!!.groupValues[1]});")
    }

    @Test
    suspend fun `arithmetic operators keep their grouping`() {
        val lines = promelaLines(
            """
                var x : int := 0
                init { }
                tran { x := ((x + 1) - (2 * x)) / 3 }
                prop { AG x == -x }
            """,
        )

        assertThat(lines).contains("x = (((x + 1) - (2 * x)) / 3);", "ltl p { [] (!$SPIN_STABLE_FLAG || ((x == (-x)))) }")
    }

    @Test
    suspend fun `boolean operators render with xor as inequality`() {
        val lines = promelaLines(
            """
                var a : bool := false
                var b : bool := true
                init { }
                tran { a := (a && b) || !(a ^^ b) }
                prop { AG true }
            """,
        )

        assertThat(lines).contains("a = ((a && b) || !((a != b)));")
    }

    @Test
    suspend fun `comparison operators render as in C`() {
        val lines = promelaLines(
            """
                var x : int := 0
                init { }
                tran { assume(x < 1 && x <= 2 && x > -3 && x >= -4 && x == 0 && x != 5) }
                prop { AG true }
            """,
        )

        assertThat(lines).contains("(((((((x < 1) && (x <= 2)) && (x > (-3))) && (x >= (-4))) && (x == 0)) && (x != 5)));")
    }

    @Test
    suspend fun `an if-then-else expression is a conditional in a step and a disjunction in the claim`() {
        val lines = promelaLines(
            """
                var x : int := 0
                init { }
                tran { x := if x < 3 then x + 1 else 0 }
                prop { AG (if x < 3 then x == 0 else x == 1) }
            """,
        )

        assertThat(lines).contains(
            "x = (((x < 3)) -> ((x + 1)) : (0));",
            "ltl p { [] (!$SPIN_STABLE_FLAG || (((((x < 3)) && ((x == 0))) || ((!((x < 3))) && ((x == 1)))))) }",
        )
    }

    @Test
    suspend fun `several init branches become a choice of atomic blocks`() {
        val lines = promelaLines(
            """
                var x : int := 0
                init { x := 1 } or { x := 2 }
                tran { }
                prop { AG true }
            """,
        )

        assertThat(lines).containsSequence(
            "init {",
            "if",
            ":: atomic {",
            "$SPIN_STABLE_FLAG = false;",
            "x = 1;",
            "$SPIN_STABLE_FLAG = true;",
            "}",
            ":: atomic {",
            "$SPIN_STABLE_FLAG = false;",
            "x = 2;",
            "$SPIN_STABLE_FLAG = true;",
            "}",
            "fi;",
        )
    }

    @Test
    suspend fun `several transition branches become options of the loop`() {
        val lines = promelaLines(
            """
                var x : int := 0
                init { }
                tran { x := 1 } or { x := 2 }
                prop { AG true }
            """,
        )

        assertThat(lines).containsSequence(
            "do",
            ":: atomic {",
            "$SPIN_STABLE_FLAG = false;",
            "x = 1;",
            "$SPIN_STABLE_FLAG = true;",
            "}",
            ":: atomic {",
            "$SPIN_STABLE_FLAG = false;",
            "x = 2;",
            "$SPIN_STABLE_FLAG = true;",
            "}",
            "od;",
        )
    }

    @Test
    suspend fun `an AG property is checked in stable states`() {
        val property = transform(
            """
                var x : int := 0
                init { }
                tran { }
                prop { AG x == 5 }
            """,
        ).property

        assertThat(property.invertVerdict).isFalse()
        assertThat(property.ltl).isEqualTo("[] (!$SPIN_STABLE_FLAG || ((x == 5)))")
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
        assertThat(property.ltl).isEqualTo("[] !($SPIN_STABLE_FLAG && ((x == 5)))")
    }

    @Test
    suspend fun `a real variable is not supported`() {
        val exception = assertThrows<BackendUnsupportedException> {
            transform(
                """
                    var r : real := 0.5
                    init { }
                    tran { }
                    prop { AG true }
                """,
            )
        }

        assertThat(exception.message).isEqualTo("Spin does not support variables of type real (r)")
    }

    @Test
    suspend fun `a for operation is not supported`() {
        assertThrows<BackendUnsupportedException> {
            transform(
                """
                    var x : int := 0
                    init { }
                    tran { for (i in 0 .. 2) { x := x + i } }
                    prop { AG true }
                """,
            )
        }
    }
}
