/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.uppaal.transformation

import hu.bme.mit.semantifyr.backend.BackendUnsupportedException
import hu.bme.mit.semantifyr.backends.uppaal.ir.UppaalEdge
import hu.bme.mit.semantifyr.backends.uppaal.ir.UppaalLocation
import hu.bme.mit.semantifyr.backends.uppaal.ir.UppaalLocationKind
import hu.bme.mit.semantifyr.backends.uppaal.ir.UppaalNta
import hu.bme.mit.semantifyr.backends.uppaal.ir.UppaalVariableDecl
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class UppaalModelTransformerTest : UppaalTransformationTestBase() {

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

    private val startToRunning = UppaalEdge("start", "running")

    private suspend fun nta(body: String): UppaalNta {
        return withTransformer {
            it.buildNta(parse("inlined oxsts of semantifyr::Anything\n${body.trimIndent()}", colors, paints))
        }
    }

    private suspend fun query(body: String): String {
        return withTransformer {
            it.transform(parse("inlined oxsts of semantifyr::Anything\n${body.trimIndent()}", colors, paints)).query
        }
    }

    private suspend fun edges(body: String): List<UppaalEdge> {
        return nta(body).templates.single().edges
    }

    @Test
    suspend fun `a model is one template with a committed start and a stable running location`() {
        val template = nta(
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
        ).templates.single()

        assertThat(template.name).isEqualTo("Model")
        assertThat(template.initialLocationId).isEqualTo("start")
        assertThat(template.locations).containsExactly(
            UppaalLocation("start", "Start", UppaalLocationKind.Committed),
            UppaalLocation("running", "Running", UppaalLocationKind.Normal),
        )
        assertThat(template.edges).containsExactly(
            startToRunning,
            UppaalEdge("running", "running", assignment = "counter = (counter + 1)"),
        )
    }

    @Test
    suspend fun `literals of one name in two enums declare apart`() {
        val declarations = nta(
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
        ).globalDeclarations

        assertThat(declarations.typedefs).containsExactly(
            "// enum test__colors__Color_t",
            "const int test__colors__Color_eRed = 0;",
            "const int test__colors__Color_eGreen = 1;",
            "// enum test__paints__Color_t",
            "const int test__paints__Color_eRed = 0;",
            "const int test__paints__Color_eBlue = 1;",
        )
        assertThat(declarations.variables).contains(
            UppaalVariableDecl("int", "color", "test__colors__Color_eRed"),
            UppaalVariableDecl("int", "paint", "test__paints__Color_eRed"),
        )
    }

    @Test
    suspend fun `variables are declared with their initializers`() {
        val declarations = nta(
            """
                var counter : int := 2
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
        ).globalDeclarations

        assertThat(declarations.typedefs).containsExactly("// enum test__colors__Color_t", "const int test__colors__Color_eRed = 0;", "const int test__colors__Color_eGreen = 1;")
        assertThat(declarations.variables).containsExactly(
            UppaalVariableDecl("int[2,2]", "counter", "2"),
            UppaalVariableDecl("bool", "flag", "true"),
            UppaalVariableDecl("int", "color", "test__colors__Color_eGreen"),
        )
    }

    @Test
    suspend fun `an integer is declared with its range when the analysis bounds it within 32 bits and with the 31-bit range otherwise`() {
        val declarations = nta(
            """
                var bounded : int := 0
                var large : int := 0
                var huge : int := 1
                var unbounded : int := 0
                init {
                }
                tran {
                    if (bounded < 3) {
                        bounded := bounded + 1
                    }
                    if (large < 100000) {
                        large := large + 25000
                    }
                    if (huge < 100000) {
                        huge := huge * 100000
                    }
                    unbounded := unbounded + 1
                }
                prop {
                    AG bounded <= 3
                }
            """,
        ).globalDeclarations

        assertThat(declarations.variables).containsExactly(
            UppaalVariableDecl("int[0,3]", "bounded", "0"),
            UppaalVariableDecl("int[0,124999]", "large", "0"),
            UppaalVariableDecl("int[-1073741824,1073741823]", "huge", "1"),
            UppaalVariableDecl("int[-1073741824,1073741823]", "unbounded", "0"),
        )
    }

    @Test
    suspend fun `variables havoced in init start at any value of their type`() {
        val template = nta(
            """
                var counter : int := 0
                var flag : bool := false
                var color : test::colors::Color := test::colors::Color::Red
                init {
                    havoc(counter)
                    havoc(flag)
                    havoc(color)
                    counter := counter + 1
                }
                tran {
                }
                prop {
                    AG counter < 5
                }
            """,
        ).templates.single()

        assertThat(template.initialLocationId).isEqualTo("start")
        assertThat(template.edges).containsExactly(
            UppaalEdge("start", "loc_0", assignment = "counter = 0"),
            UppaalEdge("start", "loc_0", assignment = "counter = 1"),
            UppaalEdge("start", "loc_0", assignment = "counter = 4"),
            UppaalEdge("start", "loc_0", assignment = "counter = 5"),
            UppaalEdge("loc_0", "loc_1", select = "havoc_flag_0 : int[0, 1]", assignment = "flag = havoc_flag_0"),
            UppaalEdge("loc_1", "loc_2", select = "havoc_color_1 : int[0, 1]", assignment = "color = havoc_color_1"),
            UppaalEdge("loc_2", "running", assignment = "counter = (counter + 1)"),
            UppaalEdge("running", "running"),
        )
    }

    @Test
    suspend fun `a variable without an initializer is rejected`() {
        val exception = assertThrows<IllegalStateException> {
            nta(
                """
                    var counter : int
                    init {
                    }
                    tran {
                    }
                    prop {
                        AG counter < 5
                    }
                """,
            )
        }

        assertThat(exception.message).isEqualTo("Variable counter has no initializer")
    }

    @Test
    suspend fun `a local variable without an initializer is rejected`() {
        val exception = assertThrows<IllegalStateException> {
            nta(
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
            )
        }

        assertThat(exception.message).isEqualTo("Local variable delta has no initializer")
    }

    @Test
    suspend fun `an assumption is an edge guard`() {
        val edges = edges(
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

        assertThat(edges).containsExactly(startToRunning, UppaalEdge("running", "running", guard = "(counter < 3)"))
    }

    @Test
    suspend fun `a sequence chains its steps through committed locations`() {
        val model = nta(
            """
                var counter : int := 0
                init {
                }
                tran {
                    assume(counter < 3)
                    counter := counter + 1
                }
                prop {
                    AG true
                }
            """,
        )

        val template = model.templates.single()
        assertThat(template.locations).contains(UppaalLocation("loc_0", "seq_loc_0", UppaalLocationKind.Committed))
        assertThat(template.edges).containsExactly(
            startToRunning,
            UppaalEdge("running", "loc_0", guard = "(counter < 3)"),
            UppaalEdge("loc_0", "running", assignment = "counter = (counter + 1)"),
        )
    }

    @Test
    suspend fun `a choice is an edge per branch between the same locations`() {
        val edges = edges(
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

        assertThat(edges).containsExactly(
            startToRunning,
            UppaalEdge("running", "running", assignment = "counter = 1"),
            UppaalEdge("running", "running"),
        )
    }

    @Test
    suspend fun `an if guards its branches with the condition and its negation`() {
        val edges = edges(
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

        assertThat(edges).containsExactly(
            startToRunning,
            UppaalEdge("running", "loc_0", guard = "(counter < 3)"),
            UppaalEdge("loc_0", "running", assignment = "counter = 1"),
            UppaalEdge("running", "loc_1", guard = "!((counter < 3))"),
            UppaalEdge("loc_1", "running"),
        )
    }

    @Test
    suspend fun `an integer havoc is an edge per value the model compares against`() {
        val edges = edges(
            """
                var counter : int := 0
                init {
                }
                tran {
                    havoc(counter)
                }
                prop {
                    AG counter < 5
                }
            """,
        )

        assertThat(edges).containsExactly(
            startToRunning,
            UppaalEdge("running", "running", assignment = "counter = 0"),
            UppaalEdge("running", "running", assignment = "counter = 4"),
            UppaalEdge("running", "running", assignment = "counter = 5"),
        )
    }

    @Test
    suspend fun `boolean and enum havocs select over the domain`() {
        val edges = edges(
            """
                var flag : bool := false
                var color : test::colors::Color := test::colors::Color::Red
                init {
                }
                tran {
                    havoc(flag)
                    havoc(color)
                }
                prop {
                    AG true
                }
            """,
        )

        assertThat(edges).containsExactly(
            startToRunning,
            UppaalEdge("running", "loc_0", select = "havoc_flag_0 : int[0, 1]", assignment = "flag = havoc_flag_0"),
            UppaalEdge("loc_0", "running", select = "havoc_color_1 : int[0, 1]", assignment = "color = havoc_color_1"),
        )
    }

    @Test
    suspend fun `a local variable is a global assigned by its declaration`() {
        val model = nta(
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
        )

        val local = model.globalDeclarations.variables.last()
        assertThat(local.name).matches("delta_l\\d+")
        assertThat(local.initialValue).isNull()
        assertThat(model.templates.single().edges).containsExactly(
            startToRunning,
            UppaalEdge("running", "loc_0", assignment = "${local.name} = 2"),
            UppaalEdge("loc_0", "running", assignment = "counter = (counter + ${local.name})"),
        )
    }

    @Test
    suspend fun `operators render in Uppaal syntax`() {
        val edges = edges(
            """
                var counter : int := 0
                var left : bool := false
                var right : bool := true
                init {
                }
                tran {
                    choice {
                        counter := ((counter + 1) - (2 * counter)) / 3
                    } or {
                        left := (left && right) || !(left ^^ right)
                    } or {
                        assume(counter < 1 && counter <= 2 && counter > -3 && counter >= -4 && counter == 0 && counter != 5)
                    } or {
                        counter := if counter < 3 then counter + 1 else 0
                    }
                }
                prop {
                    AG true
                }
            """,
        )

        assertThat(edges).containsExactly(
            startToRunning,
            UppaalEdge("running", "running", assignment = "counter = (((counter + 1) - (2 * counter)) / 3)"),
            UppaalEdge("running", "running", assignment = "left = ((left && right) || !((left != right)))"),
            UppaalEdge("running", "running", guard = "((((((counter < 1) && (counter <= 2)) && (counter > (-3))) && (counter >= (-4))) && (counter == 0)) && (counter != 5))"),
            UppaalEdge("running", "running", assignment = "counter = (((counter < 3)) ? ((counter + 1)) : (0))"),
        )
    }

    @Test
    suspend fun `init branches leave start and transition branches loop on running`() {
        val edges = edges(
            """
                var counter : int := 0
                init {
                    counter := 1
                } or {
                    counter := 2
                }
                tran {
                    counter := 3
                } or {
                    counter := 4
                }
                prop {
                    AG true
                }
            """,
        )

        assertThat(edges).containsExactly(
            UppaalEdge("start", "running", assignment = "counter = 1"),
            UppaalEdge("start", "running", assignment = "counter = 2"),
            UppaalEdge("running", "running", assignment = "counter = 3"),
            UppaalEdge("running", "running", assignment = "counter = 4"),
        )
    }

    @Test
    suspend fun `only the running location lets time pass between whole steps`() {
        val template = nta(
            """
                var counter : int := 0
                var flag : bool := false
                init {
                    choice {
                        counter := 1
                    } or {
                        havoc(flag)
                    }
                } or {
                    counter := 2
                }
                tran {
                    assume(counter < 5)
                    if (flag) {
                        choice {
                            counter := counter + 1
                        } or {
                            var delta : int := 1
                            counter := counter - delta
                        }
                    } else {
                        havoc(counter)
                    }
                } or {
                    flag := !flag
                }
                prop {
                    AG counter < 5
                }
            """,
        ).templates.single()

        val stable = template.locations.filter {
            it.kind != UppaalLocationKind.Committed
        }
        assertThat(stable).containsExactly(UppaalLocation("running", "Running", UppaalLocationKind.Normal))
        val boundaries = template.locations.filter {
            it.name.endsWith("Start") || it.name.endsWith("Running")
        }
        assertThat(boundaries.map { it.id }).containsExactly("start", "running")
    }

    @Test
    suspend fun `an AG property is an invariant over the running location`() {
        val query = query(
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

        assertThat(query).isEqualTo("A[] (Model.Running imply ((counter == 5)))")
    }

    @Test
    suspend fun `an EF property is a reachability query over the running location`() {
        val query = query(
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

        assertThat(query).isEqualTo("E<> (Model.Running and ((color == test__colors__Color_eGreen)))")
    }

    @Test
    suspend fun `a for operation is not supported`() {
        assertThrows<BackendUnsupportedException> {
            nta(
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
    }

    private suspend fun unsupportedMessage(body: String): String? {
        return assertThrows<BackendUnsupportedException> {
            nta(body)
        }.message
    }

    @Test
    suspend fun `a real variable is not supported`() {
        val message = unsupportedMessage(
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

        assertThat(message).isEqualTo("Uppaal does not support variables of type real (ratio)")
    }

    @Test
    suspend fun `a clock must be data-typed`() {
        val message = unsupportedMessage(
            """
                @semantifyr::Clock
                var elapsed : test::colors::Color := test::colors::Color::Red
                init {
                }
                tran {
                }
                prop {
                    AG true
                }
            """,
        )

        assertThat(message).isEqualTo("Uppaal supports @Clock only on data-typed variables (elapsed)")
    }

    @Test
    suspend fun `a clock is reset only to zero`() {
        val message = unsupportedMessage(
            """
                @semantifyr::Clock
                var elapsed : int := 0
                init {
                }
                tran {
                    elapsed := 5
                }
                prop {
                    AG true
                }
            """,
        )

        assertThat(message).isEqualTo("Uppaal resets clock elapsed only to 0")
    }

    @Test
    suspend fun `a clock is not reset to an expression`() {
        val message = unsupportedMessage(
            """
                @semantifyr::Clock
                var elapsed : int := 0
                var counter : int := 0
                init {
                }
                tran {
                    elapsed := counter
                }
                prop {
                    AG true
                }
            """,
        )

        assertThat(message).isEqualTo("Uppaal resets clock elapsed only to 0")
    }

    @Test
    suspend fun `a clock is not havoced`() {
        val message = unsupportedMessage(
            """
                @semantifyr::Clock
                var elapsed : int := 0
                init {
                }
                tran {
                    havoc(elapsed)
                }
                prop {
                    AG true
                }
            """,
        )

        assertThat(message).isEqualTo("Uppaal does not support havoc on clock elapsed")
    }

    @Test
    suspend fun `a clock is reset to zero`() {
        val edges = edges(
            """
                @semantifyr::Clock
                var elapsed : int := 0
                init {
                }
                tran {
                    elapsed := 0
                }
                prop {
                    AG true
                }
            """,
        )

        assertThat(edges).containsExactly(startToRunning, UppaalEdge("running", "running", assignment = "elapsed = 0"))
    }
}
