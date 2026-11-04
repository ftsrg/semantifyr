/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.nuxmv.transformation

import hu.bme.mit.semantifyr.backend.BackendUnsupportedException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class NuxmvModelTransformerTest : NuxmvTransformationTestBase() {

    private val colors = """
        package test::colors
        enum Color {
            Red,
            Green,
        }
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

        assertThat(artifacts.smv).isEqualTo(
            """
                MODULE main
                VAR
                    counter : integer;
                    counter__init_b0_seed : integer;
                    counter__tran_b0_1 : integer;
                INIT
                    (
                          (counter__init_b0_seed = 0)
                        & (counter = counter__init_b0_seed)
                    )
                TRANS
                    (
                          (counter__tran_b0_1 = (counter + 1))
                        & (next(counter) = counter__tran_b0_1)
                    )

            """.trimIndent(),
        )
        assertThat(artifacts.property.invariant).isEqualTo("(counter < 3)")
    }

    @Test
    suspend fun `a variable without an initializer starts unconstrained`() {
        val lines = smvLines(
            """
                var counter : int
                init {
                }
                tran {
                }
                prop {
                    AG counter == 0
                }
            """,
        )

        assertThat(lines).containsSequence("INIT", "(", "(counter = counter__init_b0_seed)", ")")
    }

    @Test
    suspend fun `boolean and enum variables get their domains`() {
        val lines = smvLines(
            """
                var flag : bool := true
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

        assertThat(lines).contains("flag : boolean;", "color : { Red, Green };", "(flag__init_b0_seed = TRUE)", "& (color__init_b0_seed = Green)")
    }

    @Test
    suspend fun `an assumption constrains the step`() {
        val lines = smvLines(
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

        assertThat(lines).containsSequence("TRANS", "(", "((counter < 3))", "& (next(counter) = counter)", ")")
    }

    @Test
    suspend fun `a choice selects its branch with an input variable`() {
        val lines = smvLines(
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

        assertThat(lines).containsSequence("IVAR", "nondet_tran_b0_0 : 0..1;")
        assertThat(lines).containsSequence(
            "(case nondet_tran_b0_0 = 0 : (counter__tran_b0_1 = 1); nondet_tran_b0_0 = 1 : TRUE; esac)",
            "& (counter__tran_b0_2 = case nondet_tran_b0_0 = 0 : counter__tran_b0_1; nondet_tran_b0_0 = 1 : counter; esac)",
            "& (next(counter) = counter__tran_b0_2)",
        )
    }

    @Test
    suspend fun `a choice in init selects its branch with a frozen variable`() {
        val lines = smvLines(
            """
                var counter : int := 0
                init {
                    choice {
                        counter := 1
                    } or {
                        counter := 2
                    }
                }
                tran {
                }
                prop {
                    AG true
                }
            """,
        )

        assertThat(lines).containsSequence("FROZENVAR", "nondet_init_b0_0 : 0..1;")
        assertThat(lines).doesNotContain("IVAR")
    }

    @Test
    suspend fun `an if merges both branches by its guard`() {
        val lines = smvLines(
            """
                var counter : int := 0
                init {
                }
                tran {
                    if (counter < 3) {
                        counter := 1
                    } else {
                        counter := 2
                    }
                }
                prop {
                    AG true
                }
            """,
        )

        assertThat(lines).containsSequence(
            "(case (counter < 3) : (counter__tran_b0_1 = 1); TRUE : (counter__tran_b0_2 = 2); esac)",
            "& (counter__tran_b0_3 = case (counter < 3) : counter__tran_b0_1; TRUE : counter__tran_b0_2; esac)",
            "& (next(counter) = counter__tran_b0_3)",
        )
    }

    @Test
    suspend fun `an if without an else keeps the value on the else branch`() {
        val lines = smvLines(
            """
                var counter : int := 0
                init {
                }
                tran {
                    if (counter < 3) {
                        counter := 1
                    }
                }
                prop {
                    AG true
                }
            """,
        )

        assertThat(lines).containsSequence(
            "(case (counter < 3) : (counter__tran_b0_1 = 1); TRUE : TRUE; esac)",
            "& (counter__tran_b0_2 = case (counter < 3) : counter__tran_b0_1; TRUE : counter; esac)",
        )
    }

    @Test
    suspend fun `a havoc binds the next value to an unconstrained prime`() {
        val lines = smvLines(
            """
                var counter : int := 0
                var flag : bool := false
                var color : test::colors::Color := test::colors::Color::Red
                init {
                }
                tran {
                    havoc(counter)
                    havoc(flag)
                    havoc(color)
                }
                prop {
                    AG true
                }
            """,
        )

        assertThat(lines).containsSequence(
            "TRANS",
            "(",
            "(next(counter) = counter__tran_b0_1)",
            "& (next(flag) = flag__tran_b0_1)",
            "& (next(color) = color__tran_b0_1)",
            ")",
        )
    }

    @Test
    suspend fun `a local variable becomes a primed value of the step`() {
        val smv = transform(
            """
                var counter : int := 0
                init {
                }
                tran {
                    var delta : int := 2
                    counter := counter + delta
                }
                prop {
                    AG true
                }
            """,
        ).smv

        val local = Regex("\\((delta_\\d+)__tran_b0_1 = 2\\)").find(smv)
        assertThat(local).isNotNull()
        assertThat(smv).contains("(counter__tran_b0_1 = (counter + ${local!!.groupValues[1]}__tran_b0_1))")
    }

    @Test
    suspend fun `arithmetic, boolean and comparison operators render in SMV syntax`() {
        val lines = smvLines(
            """
                var counter : int := 0
                var left : bool := false
                var right : bool := true
                init {
                }
                tran {
                    counter := ((counter + 1) - (2 * counter)) / 3
                    left := (left && right) || !(left ^^ right)
                    assume(counter < 1 && counter <= 2 && counter > -3 && counter >= -4 && counter == 0 && counter != 5)
                }
                prop {
                    AG counter == -counter
                }
            """,
        )

        assertThat(lines).contains(
            "(counter__tran_b0_1 = (((counter + 1) - (2 * counter)) / 3))",
            "& (left__tran_b0_1 = ((left & right) | !((left xor right))))",
            "& (((((((counter__tran_b0_1 < 1) & (counter__tran_b0_1 <= 2)) & (counter__tran_b0_1 > (-3))) & (counter__tran_b0_1 >= (-4))) & (counter__tran_b0_1 = 0)) & (counter__tran_b0_1 != 5)))",
        )
    }

    @Test
    suspend fun `an if-then-else expression is a case expression`() {
        val artifacts = transform(
            """
                var counter : int := 0
                init {
                }
                tran {
                    counter := if counter < 3 then counter + 1 else 0
                }
                prop {
                    AG (if counter < 3 then counter == 0 else counter == 1)
                }
            """,
        )

        assertThat(artifacts.smv).contains("(counter__tran_b0_1 = case (counter < 3) : (counter + 1); TRUE : 0; esac)")
        assertThat(artifacts.property.invariant).isEqualTo("case (counter < 3) : (counter = 0); TRUE : (counter = 1); esac")
    }

    @Test
    suspend fun `several transition branches become a disjunction`() {
        val lines = smvLines(
            """
                var counter : int := 0
                init {
                }
                tran {
                    counter := 1
                } or {
                    counter := 2
                }
                prop {
                    AG true
                }
            """,
        )

        assertThat(lines).containsSequence(
            "TRANS",
            "(",
            "(counter__tran_b0_1 = 1)",
            "& (next(counter) = counter__tran_b0_1)",
            ")",
            "| (",
            "(counter__tran_b1_1 = 2)",
            "& (next(counter) = counter__tran_b1_1)",
            ")",
        )
    }

    private fun declaredNames(smv: String): List<String> {
        val sections = setOf("VAR", "IVAR", "FROZENVAR")
        var inSection = false
        val names = mutableListOf<String>()
        for (line in smv.lines()) {
            if (!line.startsWith(" ")) {
                inSection = line.trim() in sections
                continue
            }
            if (inSection) {
                names += line.trim().substringBefore(" :")
            }
        }
        return names
    }

    @Test
    suspend fun `choices in several transition branches get distinct input variables`() {
        val smv = transform(
            """
                var counter : int := 0
                var step : int := 0
                init {
                }
                tran {
                    assume(step == 0)
                    choice {
                        counter := 1
                    } or {
                        counter := 2
                    }
                    step := 1
                } or {
                    assume(step == 1)
                    choice {
                        counter := 3
                    } or {
                        counter := 4
                    }
                    step := 2
                }
                prop {
                    AG true
                }
            """,
        ).smv

        assertThat(declaredNames(smv)).doesNotHaveDuplicates()
        assertThat(smv.lines().map { it.trim() }).containsSequence("IVAR", "nondet_tran_b0_0 : 0..1;", "nondet_tran_b1_0 : 0..1;")
    }

    @Test
    suspend fun `several init branches get distinct seeds and frozen variables`() {
        val smv = transform(
            """
                var counter : int := 0
                init {
                    choice {
                        counter := 1
                    } or {
                        counter := 2
                    }
                } or {
                    choice {
                        counter := 3
                    } or {
                        counter := 4
                    }
                }
                tran {
                }
                prop {
                    AG true
                }
            """,
        ).smv

        assertThat(declaredNames(smv)).doesNotHaveDuplicates()
        assertThat(smv.lines().map { it.trim() }).containsSequence("FROZENVAR", "nondet_init_b0_0 : 0..1;", "nondet_init_b1_0 : 0..1;")
    }

    @Test
    suspend fun `an AG property keeps the verdict and the body`() {
        val property = transform(
            """
                var counter : int := 0
                init {
                }
                tran {
                }
                prop {
                    AG counter == 5
                }
            """,
        ).property

        assertThat(property.invertVerdict).isFalse()
        assertThat(property.invariant).isEqualTo("(counter = 5)")
    }

    @Test
    suspend fun `an EF property is checked as the negated invariant with an inverted verdict`() {
        val property = transform(
            """
                var counter : int := 0
                init {
                }
                tran {
                }
                prop {
                    EF counter == 5
                }
            """,
        ).property

        assertThat(property.invertVerdict).isTrue()
        assertThat(property.invariant).isEqualTo("!((counter = 5))")
    }

    @Test
    suspend fun `a real variable is not supported`() {
        val exception = assertThrows<BackendUnsupportedException> {
            transform(
                """
                    var ratio : real := 0.5
                    init {
                    }
                    tran {
                    }
                    prop {
                        AG true
                    }
                """,
            )
        }

        assertThat(exception.message).isEqualTo("nuXmv does not support variables of type real (ratio)")
    }

    @Test
    suspend fun `a for operation is not supported`() {
        val exception = assertThrows<BackendUnsupportedException> {
            transform(
                """
                    var counter : int := 0
                    init {
                    }
                    tran {
                        for (index in 0..2) {
                            counter := counter + index
                        }
                    }
                    prop {
                        AG true
                    }
                """,
            )
        }

        assertThat(exception.message).isEqualTo("nuXmv does not support for-operations")
    }
}
