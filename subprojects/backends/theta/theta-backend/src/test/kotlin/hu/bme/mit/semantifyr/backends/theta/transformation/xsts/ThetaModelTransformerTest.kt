/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.theta.transformation.xsts

import hu.bme.mit.semantifyr.backend.BackendUnsupportedException
import hu.bme.mit.semantifyr.backend.testModel
import hu.bme.mit.semantifyr.compiler.pipeline.inlining.TransitionTracerNames
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ThetaModelTransformerTest : ThetaTransformationTestBase() {

    private val colors = """
        package test::colors
        enum Color {
            Red,
            Green,
        }
    """

    private val paints = """
        package test::paints
        enum Color {
            Red,
            Blue,
        }
    """

    private suspend fun xsts(body: String): String {
        return xstsOf("inlined oxsts of semantifyr::Anything\n${body.trimIndent()}")
    }

    private suspend fun xstsOf(model: String): String {
        return withTransformer {
            serialize(it.transform(parse(model, colors, paints), testXstsUri).xsts)
        }
    }

    private suspend fun xstsLines(body: String): List<String> {
        return xstsLinesOf("inlined oxsts of semantifyr::Anything\n${body.trimIndent()}")
    }

    private suspend fun xstsLinesOf(model: String): List<String> {
        return xstsOf(model).lines().map {
            it.trim()
        }.filter {
            it.isNotEmpty()
        }
    }

    @Test
    suspend fun `a model renders variables, the transitions and the property`() {
        val lines = xstsLines(
            """
                var counter : int := 0
                init {
                    counter := 1
                }
                tran {
                    counter := counter + 1
                }
                prop {
                    AG counter < 3
                }
            """,
        )

        assertThat(lines).containsSequence(
            "var counter: integer = 0",
            "ctrl var __semantifyr_initialized: boolean = false",
        )
        assertThat(lines).containsSequence(
            "trans {",
            "counter := counter + 1;",
            "}",
        )
        assertThat(lines).containsSequence(
            "init {",
            "counter := 1;",
            "__semantifyr_initialized := true;",
            "}",
        )
        assertThat(lines).containsSequence(
            "prop { !__semantifyr_initialized || counter < 3",
            "}",
        )
    }

    @Test
    suspend fun `the serialized model separates its lines with a line feed alone`() {
        val xsts = xstsOf(testModel("line_feeds.semantifyr"))

        assertThat(xsts).contains("\n").doesNotContain("\r")
    }

    @Test
    suspend fun `enums of one name in two packages declare apart and keep their literal names`() {
        val lines = xstsLines(
            """
                var color : test::colors::Color := test::colors::Color::Red
                var paint : test::paints::Color := test::paints::Color::Red
                init {
                }
                tran {
                }
                prop {
                    AG color == test::colors::Color::Red
                }
            """,
        )

        assertThat(lines).contains(
            "type test\$\$colors\$\$Color : { Red, Green",
            "type test\$\$paints\$\$Color : { Red, Blue",
            "var color: test\$\$colors\$\$Color = test\$\$colors\$\$Color.Red",
            "var paint: test\$\$paints\$\$Color = test\$\$paints\$\$Color.Red",
        )
    }

    @Test
    suspend fun `an enum only a local uses is declared`() {
        val lines = xstsLines(
            """
                var counter : int := 0
                init {
                }
                tran {
                    var shade : test::colors::Color := test::colors::Color::Green
                    if (shade == test::colors::Color::Green) {
                        counter := 1
                    }
                }
                prop {
                    AG counter < 3
                }
            """,
        )

        assertThat(lines).contains("type test\$\$colors\$\$Color : { Red, Green")
    }

    @Test
    suspend fun `an enum whose literals no variable holds is declared`() {
        val lines = xstsLines(
            """
                var counter : int := 0
                init {
                }
                tran {
                    assume(if test::colors::Color::Green == test::colors::Color::Green then counter < 2 else counter < 3)
                    counter := counter + 1
                }
                prop {
                    AG counter < 3
                }
            """,
        )

        assertThat(lines).contains("type test\$\$colors\$\$Color : { Red, Green")
    }

    @Test
    suspend fun `variables keep their types and initializers`() {
        val lines = xstsLines(
            """
                var counter : int := 0
                var flag : bool := true
                var color : test::colors::Color := test::colors::Color::Green
                init {
                }
                tran {
                }
                prop {
                    AG true
                }
            """,
        )

        assertThat(lines).containsSequence("type test\$\$colors\$\$Color : { Red, Green", "}", "var counter: integer = 0", "var flag: boolean = true", "var color: test\$\$colors\$\$Color = test\$\$colors\$\$Color.Green")
    }

    @Test
    suspend fun `a variable without an initializer is rejected`() {
        val exception = assertThrows<IllegalStateException> {
            xsts(
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
        }

        assertThat(exception.message).isEqualTo("Variable counter has no initializer")
    }

    @Test
    suspend fun `a variable annotated Control is a control variable`() {
        val lines = xstsLines(
            """
                @Control
                var counter : int := 0
                init {
                }
                tran {
                    counter := counter + 1
                }
                prop {
                    AG true
                }
            """,
        )

        assertThat(lines).contains("ctrl var counter: integer = 0")
    }

    @Test
    suspend fun `a tracer, declared without the Control annotation, is declared outside the control set`() {
        val tracer = TransitionTracerNames.tracerName(0)
        val lines = xstsLines(
            """
                var $tracer : bool := false
                init {
                }
                tran {
                    $tracer := true
                }
                prop {
                    AG true
                }
            """,
        )

        assertThat(lines).contains("var $tracer: boolean = false")
    }

    @Test
    suspend fun `assumptions, choices and ifs map to their XSTS forms`() {
        val lines = xstsLinesOf(testModel("assumptions_choices_and_ifs.semantifyr"))

        assertThat(lines).containsSequence(
            "trans {",
            "assume counter < 3;",
            "choice {",
            "counter := 1;",
            "} or {",
            "}",
            "if ( counter < 3 ) {",
            "counter := 1;",
            "} else {",
            "counter := 2;",
            "}",
            "if ( counter > 3 ) {",
            "counter := 3;",
            "}",
            "}",
        )
    }

    @Test
    suspend fun `havocs are native`() {
        val lines = xstsLines(
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

        assertThat(lines).containsSequence("havoc counter;", "havoc flag;", "havoc color;")
    }

    @Test
    suspend fun `local variables are declared in the step`() {
        val lines = xstsLines(
            """
                var counter : int := 0
                init {
                }
                tran {
                    var delta : int := 2
                    var extra : int := 3
                    counter := counter + delta + extra
                }
                prop {
                    AG true
                }
            """,
        )

        assertThat(lines).containsSequence("local var delta : integer = 2;", "local var extra : integer = 3;", "counter := counter + delta + extra;")
    }

    @Test
    suspend fun `operators keep the meaning of the source grouping`() {
        val lines = xstsLinesOf(testModel("operator_grouping.semantifyr"))

        assertThat(lines).containsSequence(
            "counter := ( counter + 1 - 2 * counter ) / 3;",
            "counter := counter - ( 1 - counter );",
            "left := left && right || !( left xor right );",
            "left := left && ( right || left );",
            "left := ( left || right ) && left;",
            "left := ( left xor right ) xor left;",
            "left := ( counter == 0 ) == left;",
            "left := counter < 1 != counter < 2;",
            "assume counter < 1 && counter <= 2 && counter > - 3 && counter >= - 4 && counter == 0 && counter != 5;",
            "counter := if counter < 3 then counter + 1 else 0;",
        )
        assertThat(lines).contains("prop { !__semantifyr_initialized || counter == - ( counter + 1 )")
    }

    @Test
    suspend fun `several branches become an or of sequences`() {
        val lines = xstsLines(
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

        assertThat(lines).containsSequence("trans {", "counter := 1;", "} or {", "counter := 2;", "}")
    }

    @Test
    suspend fun `every init branch ends by setting the initialized flag`() {
        val lines = xstsLines(
            """
                var counter : int := 0
                init {
                    counter := 1
                } or {
                    assume(counter > 0)
                }
                tran {
                }
                prop {
                    AG true
                }
            """,
        )

        assertThat(lines).containsSequence(
            "init {",
            "counter := 1;",
            "__semantifyr_initialized := true;",
            "} or {",
            "assume counter > 0;",
            "__semantifyr_initialized := true;",
            "}",
        )
    }

    @Test
    suspend fun `an AG property is the invariant once initialized`() {
        val lines = xstsLines(
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
        )

        assertThat(lines).contains("prop { !__semantifyr_initialized || counter == 5")
    }

    @Test
    suspend fun `an EF property is the negated invariant once initialized`() {
        val lines = xstsLines(
            """
                var color : test::colors::Color := test::colors::Color::Red
                init {
                }
                tran {
                }
                prop {
                    EF color == test::colors::Color::Green
                }
            """,
        )

        assertThat(lines).contains("prop { !__semantifyr_initialized || !( color == test\$\$colors\$\$Color.Green )")
    }

    @Test
    suspend fun `a real variable is not supported`() {
        val exception = assertThrows<BackendUnsupportedException> {
            xsts(
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

        assertThat(exception.message).isEqualTo("Theta does not support variables of type real")
    }

    @Test
    suspend fun `a for operation over an inclusive range runs up to and including its end`() {
        val lines = xstsLines(
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

        assertThat(lines).containsSequence("for", "index\$\$\$local0", "from 0 to 2 + 1 do {", "counter := counter + index\$\$\$local0;", "}")
    }

    @Test
    suspend fun `a for operation over an exclusive range stops before its end`() {
        val lines = xstsLines(
            """
                var counter : int := 0
                init {
                }
                tran {
                    for (index in 0..<3) {
                        counter := counter + index
                    }
                }
                prop {
                    AG true
                }
            """,
        )

        assertThat(lines).containsSequence("for", "index\$\$\$local0", "from 0 to 3 do {", "counter := counter + index\$\$\$local0;", "}")
    }

    @Test
    suspend fun `a real literal is not supported`() {
        assertThrows<BackendUnsupportedException> {
            xsts(
                """
                    var flag : bool := false
                    init {
                    }
                    tran {
                        flag := 1.5 > 1.0
                    }
                    prop {
                        AG true
                    }
                """,
            )
        }
    }

    @Test
    suspend fun `every main step starts with a bounded whole delay that advances each clock up to its saturation, and init waits for nothing`() {
        val lines = xstsLines(
            """
                var elapsed : clock := 0
                var late : bool := false
                init {
                }
                tran {
                    assume(elapsed <= 2)
                    choice {
                        assume(elapsed >= 2)
                        late := true
                        elapsed := 0
                    } or {
                    }
                }
                prop {
                    AG !late
                }
            """,
        )

        assertThat(lines).contains("var __semantifyr_delay: integer = 0")
        assertThat(lines).containsSequence(
            "trans {",
            "havoc __semantifyr_delay;",
            "assume __semantifyr_delay >= 0 && __semantifyr_delay <= 3;",
            "elapsed := if elapsed + __semantifyr_delay >= 3 then 3 else elapsed + __semantifyr_delay;",
            "assume elapsed <= 2;",
        )
        assertThat(lines).containsSequence(
            "init {",
            "__semantifyr_initialized := true;",
            "}",
        )
    }
}
