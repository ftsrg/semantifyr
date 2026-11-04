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
        enum Color {
            Red,
            Green,
        }
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
                var counter : int := 0
                init {
                }
                tran {
                    counter := counter + 1
                }
                prop {
                    AG counter < 3
                }
            """,
        )

        assertThat(artifacts.promela).isEqualTo(
            """
                |int counter = 0;
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
                |        counter = (counter + 1);
                |        $SPIN_STABLE_FLAG = true;
                |    }
                |    od;
                |}
                |
                |ltl p { [] (!$SPIN_STABLE_FLAG || ((counter < 3))) }
                |
            """.trimMargin(),
        )
    }

    @Test
    suspend fun `a variable with an initializer is declared with it`() {
        val lines = promelaLines(
            """
                var counter : int := 2
                var flag : bool := true
                init {
                }
                tran {
                }
                prop {
                    AG true
                }
            """,
        )

        assertThat(lines).containsSequence("int counter = 2;", "bool flag = true;")
    }

    @Test
    suspend fun `variables without an initializer start at any value of their type`() {
        val lines = promelaLines(
            """
                var counter : int
                var flag : bool
                var color : test::colors::Color
                init {
                    counter := counter + 1
                }
                tran {
                }
                prop {
                    AG counter < 5
                }
            """,
        )

        assertThat(lines).containsSequence(
            "init {",
            "atomic {",
            "if",
            ":: counter = 1",
            ":: counter = 4",
            ":: counter = 5",
            "fi;",
            "if",
            ":: flag = false",
            ":: flag = true",
            "fi;",
            "if",
            ":: color = Red",
            ":: color = Green",
            "fi;",
            "}",
            "atomic {",
            "$SPIN_STABLE_FLAG = false;",
            "counter = (counter + 1);",
            "$SPIN_STABLE_FLAG = true;",
            "}",
        )
    }

    @Test
    suspend fun `variables with an initializer are not chosen again`() {
        val lines = promelaLines(
            """
                var counter : int := 0
                init {
                }
                tran {
                }
                prop {
                    AG counter < 5
                }
            """,
        )

        assertThat(lines).containsSequence("init {", "atomic {", "$SPIN_STABLE_FLAG = false;", "skip;")
    }

    @Test
    suspend fun `a local variable without an initializer starts at any value in every step`() {
        val promela = transform(
            """
                var counter : int := 0
                init {
                }
                tran {
                    var delta : int
                    counter := delta
                }
                prop {
                    AG counter < 3
                }
            """,
        ).promela

        val local = Regex("int (delta_\\d+);").find(promela)!!.groupValues[1]
        assertThat(promela.lines().map { it.trim() }).containsSequence(
            "int $local;",
            "if",
            ":: $local = 0",
            ":: $local = 3",
            "fi;",
            "counter = $local;",
        )
    }

    @Test
    suspend fun `an enum variable declares its mtype`() {
        val lines = promelaLines(
            """
                var color : test::colors::Color := test::colors::Color::Green
                init {
                }
                tran {
                }
                prop {
                    AG color == test::colors::Color::Red
                }
            """,
        )

        assertThat(lines).contains("mtype:Color = { Red, Green };", "mtype:Color color = Green;")
        assertThat(lines).contains("ltl p { [] (!$SPIN_STABLE_FLAG || ((color == Red))) }")
    }

    @Test
    suspend fun `an assumption is a blocking guard`() {
        val lines = promelaLines(
            """
                var counter : int := 0
                init {
                }
                tran {
                    assume(counter < 3)
                }
                prop {
                    AG true
                }
            """,
        )

        assertThat(lines).containsSequence("$SPIN_STABLE_FLAG = false;", "((counter < 3));", "$SPIN_STABLE_FLAG = true;")
    }

    @Test
    suspend fun `a choice renders an atomic option per branch`() {
        val lines = promelaLines(
            """
                var counter : int := 0
                init {
                }
                tran {
                    choice {
                        counter := 1
                    } or {
                    }
                }
                prop {
                    AG true
                }
            """,
        )

        assertThat(lines).containsSequence("if", ":: atomic {", "counter = 1;", "}", ":: skip", "fi;")
    }

    @Test
    suspend fun `a choice option starting with an if starts with skip so the if's else does not guard the option`() {
        val lines = promelaLines(
            """
                var counter : int := 0
                init { }
                tran { choice { if (counter < 3) { counter := 1 } } or { if (counter > 5) { counter := 2 } } }
                prop { AG true }
            """,
        )

        assertThat(lines).containsSequence("if", ":: atomic {", "skip;", "if", ":: ((counter < 3)) ->")
        assertThat(lines).containsSequence(":: atomic {", "skip;", "if", ":: ((counter > 5)) ->")
    }

    @Test
    suspend fun `an if with an else renders both guarded options`() {
        val lines = promelaLines(
            """
                var counter : int := 0
                init { }
                tran { if (counter < 3) { counter := 1 } else { counter := 2 } }
                prop { AG true }
            """,
        )

        assertThat(lines).containsSequence("if", ":: ((counter < 3)) ->", "counter = 1;", ":: else ->", "counter = 2;", "fi;")
    }

    @Test
    suspend fun `an if without an else skips on the else option`() {
        val lines = promelaLines(
            """
                var counter : int := 0
                init { }
                tran { if (counter < 3) { counter := 1 } }
                prop { AG true }
            """,
        )

        assertThat(lines).containsSequence("if", ":: ((counter < 3)) ->", "counter = 1;", ":: else -> skip", "fi;")
    }

    @Test
    suspend fun `an integer havoc chooses among the values the model compares against`() {
        val lines = promelaLines(
            """
                var counter : int := 0
                init { }
                tran { havoc(counter) }
                prop { AG counter < 5 }
            """,
        )

        assertThat(lines).containsSequence("if", ":: counter = 0", ":: counter = 4", ":: counter = 5", "fi;")
    }

    @Test
    suspend fun `a boolean havoc chooses both values`() {
        val lines = promelaLines(
            """
                var flag : bool := false
                init { }
                tran { havoc(flag) }
                prop { AG true }
            """,
        )

        assertThat(lines).containsSequence("if", ":: flag = false", ":: flag = true", "fi;")
    }

    @Test
    suspend fun `an enum havoc chooses every literal`() {
        val lines = promelaLines(
            """
                var color : test::colors::Color := test::colors::Color::Red
                init { }
                tran { havoc(color) }
                prop { AG true }
            """,
        )

        assertThat(lines).containsSequence("if", ":: color = Red", ":: color = Green", "fi;")
    }

    @Test
    suspend fun `a local variable is declared inside the step`() {
        val promela = transform(
            """
                var counter : int := 0
                init { }
                tran { var delta : int := 2 counter := counter + delta }
                prop { AG true }
            """,
        ).promela

        val local = Regex("int (delta_\\d+) = 2;").find(promela)
        assertThat(local).isNotNull()
        assertThat(promela).contains("counter = (counter + ${local!!.groupValues[1]});")
    }

    @Test
    suspend fun `arithmetic operators keep their grouping`() {
        val lines = promelaLines(
            """
                var counter : int := 0
                init { }
                tran { counter := ((counter + 1) - (2 * counter)) / 3 }
                prop { AG counter == -counter }
            """,
        )

        assertThat(lines).contains("counter = (((counter + 1) - (2 * counter)) / 3);", "ltl p { [] (!$SPIN_STABLE_FLAG || ((counter == (-counter)))) }")
    }

    @Test
    suspend fun `boolean operators render with xor as inequality`() {
        val lines = promelaLines(
            """
                var left : bool := false
                var right : bool := true
                init { }
                tran { left := (left && right) || !(left ^^ right) }
                prop { AG true }
            """,
        )

        assertThat(lines).contains("left = ((left && right) || !((left != right)));")
    }

    @Test
    suspend fun `comparison operators render as in C`() {
        val lines = promelaLines(
            """
                var counter : int := 0
                init { }
                tran { assume(counter < 1 && counter <= 2 && counter > -3 && counter >= -4 && counter == 0 && counter != 5) }
                prop { AG true }
            """,
        )

        assertThat(lines).contains("(((((((counter < 1) && (counter <= 2)) && (counter > (-3))) && (counter >= (-4))) && (counter == 0)) && (counter != 5)));")
    }

    @Test
    suspend fun `an if-then-else expression is a conditional in a step and a disjunction in the claim`() {
        val lines = promelaLines(
            """
                var counter : int := 0
                init { }
                tran { counter := if counter < 3 then counter + 1 else 0 }
                prop { AG (if counter < 3 then counter == 0 else counter == 1) }
            """,
        )

        assertThat(lines).contains(
            "counter = (((counter < 3)) -> ((counter + 1)) : (0));",
            "ltl p { [] (!$SPIN_STABLE_FLAG || (((((counter < 3)) && ((counter == 0))) || ((!((counter < 3))) && ((counter == 1)))))) }",
        )
    }

    @Test
    suspend fun `several init branches become a choice of atomic blocks`() {
        val lines = promelaLines(
            """
                var counter : int := 0
                init { counter := 1 } or { counter := 2 }
                tran { }
                prop { AG true }
            """,
        )

        assertThat(lines).containsSequence(
            "init {",
            "if",
            ":: atomic {",
            "$SPIN_STABLE_FLAG = false;",
            "counter = 1;",
            "$SPIN_STABLE_FLAG = true;",
            "}",
            ":: atomic {",
            "$SPIN_STABLE_FLAG = false;",
            "counter = 2;",
            "$SPIN_STABLE_FLAG = true;",
            "}",
            "fi;",
        )
    }

    @Test
    suspend fun `several transition branches become options of the loop`() {
        val lines = promelaLines(
            """
                var counter : int := 0
                init { }
                tran { counter := 1 } or { counter := 2 }
                prop { AG true }
            """,
        )

        assertThat(lines).containsSequence(
            "do",
            ":: atomic {",
            "$SPIN_STABLE_FLAG = false;",
            "counter = 1;",
            "$SPIN_STABLE_FLAG = true;",
            "}",
            ":: atomic {",
            "$SPIN_STABLE_FLAG = false;",
            "counter = 2;",
            "$SPIN_STABLE_FLAG = true;",
            "}",
            "od;",
        )
    }

    @Test
    suspend fun `an AG property is checked in stable states`() {
        val property = transform(
            """
                var counter : int := 0
                init { }
                tran { }
                prop { AG counter == 5 }
            """,
        ).property

        assertThat(property.invertVerdict).isFalse()
        assertThat(property.ltl).isEqualTo("[] (!$SPIN_STABLE_FLAG || ((counter == 5)))")
    }

    @Test
    suspend fun `an EF property is checked as the negated invariant with an inverted verdict`() {
        val property = transform(
            """
                var counter : int := 0
                init { }
                tran { }
                prop { EF counter == 5 }
            """,
        ).property

        assertThat(property.invertVerdict).isTrue()
        assertThat(property.ltl).isEqualTo("[] !($SPIN_STABLE_FLAG && ((counter == 5)))")
    }

    @Test
    suspend fun `the stable flag is written only at the boundaries of whole steps`() {
        val promela = transform(
            """
                var counter : int
                var flag : bool := false
                init { choice { counter := 1 } or { havoc(flag) } } or { counter := 2 }
                tran {
                    assume(counter < 5)
                    if (flag) { choice { counter := counter + 1 } or { var delta : int := 1 counter := counter - delta } } else { havoc(counter) }
                } or {
                    flag := !flag
                }
                prop { AG counter < 5 }
            """,
        ).promela
        val lines = promela.lines()
        val flagLines = lines.withIndex().filter {
            SPIN_STABLE_FLAG in it.value && !it.value.startsWith("bool ") && !it.value.startsWith("ltl ")
        }

        assertThat(flagLines).hasSize(8)
        for ((index, line) in flagLines) {
            if (line == "        $SPIN_STABLE_FLAG = false;") {
                assertThat(lines[index - 1]).matches("    (:: )?atomic \\{")
            } else {
                assertThat(line).isEqualTo("        $SPIN_STABLE_FLAG = true;")
                assertThat(lines[index + 1]).isEqualTo("    }")
            }
        }
    }

    @Test
    suspend fun `a real variable is not supported`() {
        val exception = assertThrows<BackendUnsupportedException> {
            transform(
                """
                    var ratio : real := 0.5
                    init { }
                    tran { }
                    prop { AG true }
                """,
            )
        }

        assertThat(exception.message).isEqualTo("Spin does not support variables of type real (ratio)")
    }

    @Test
    suspend fun `a for operation is not supported`() {
        assertThrows<BackendUnsupportedException> {
            transform(
                """
                    var counter : int := 0
                    init { }
                    tran { for (index in 0..2) { counter := counter + index } }
                    prop { AG true }
                """,
            )
        }
    }
}
