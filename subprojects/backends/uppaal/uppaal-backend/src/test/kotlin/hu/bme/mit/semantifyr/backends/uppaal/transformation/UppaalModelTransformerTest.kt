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
        enum Color { Red, Green }
    """

    private val startToRunning = UppaalEdge("start", "running")

    private suspend fun nta(body: String): UppaalNta {
        return withTransformer {
            it.buildNta(parse("inlined oxsts of semantifyr::Anything\n${body.trimIndent()}", colors))
        }
    }

    private suspend fun query(body: String): String {
        return withTransformer {
            it.transform(parse("inlined oxsts of semantifyr::Anything\n${body.trimIndent()}", colors)).query
        }
    }

    private suspend fun edges(body: String): List<UppaalEdge> {
        return nta(body).templates.single().edges
    }

    @Test
    suspend fun `a model is one template with a committed start and a stable running location`() {
        val template = nta(
            """
                var x : int := 0
                init { }
                tran { x := x + 1 }
                prop { AG x < 3 }
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
            UppaalEdge("running", "running", assignment = "x = (x + 1)"),
        )
    }

    @Test
    suspend fun `variables are declared with their initializers`() {
        val declarations = nta(
            """
                var x : int := 2
                var b : bool := true
                var c : test::colors::Color := test::colors::Color::Green
                init { }
                tran { }
                prop { AG true }
            """,
        ).globalDeclarations

        assertThat(declarations.typedefs).containsExactly("// enum Color", "const int Red = 0;", "const int Green = 1;")
        assertThat(declarations.variables).containsExactly(
            UppaalVariableDecl("int", "x", "2"),
            UppaalVariableDecl("bool", "b", "true"),
            UppaalVariableDecl("int", "c", "Green"),
        )
    }

    @Test
    suspend fun `an assumption is an edge guard`() {
        val edges = edges(
            """
                var x : int := 0
                init { }
                tran { assume(x < 3) }
                prop { AG true }
            """,
        )

        assertThat(edges).containsExactly(startToRunning, UppaalEdge("running", "running", guard = "(x < 3)"))
    }

    @Test
    suspend fun `a sequence chains its steps through committed locations`() {
        val model = nta(
            """
                var x : int := 0
                init { }
                tran { assume(x < 3) x := x + 1 }
                prop { AG true }
            """,
        )

        val template = model.templates.single()
        assertThat(template.locations).contains(UppaalLocation("loc_0", "seq_loc_0", UppaalLocationKind.Committed))
        assertThat(template.edges).containsExactly(
            startToRunning,
            UppaalEdge("running", "loc_0", guard = "(x < 3)"),
            UppaalEdge("loc_0", "running", assignment = "x = (x + 1)"),
        )
    }

    @Test
    suspend fun `a choice is an edge per branch between the same locations`() {
        val edges = edges(
            """
                var x : int := 0
                init { }
                tran { choice { x := 1 } or { } }
                prop { AG true }
            """,
        )

        assertThat(edges).containsExactly(
            startToRunning,
            UppaalEdge("running", "running", assignment = "x = 1"),
            UppaalEdge("running", "running"),
        )
    }

    @Test
    suspend fun `an if guards its branches with the condition and its negation`() {
        val edges = edges(
            """
                var x : int := 0
                init { }
                tran { if (x < 3) { x := 1 } }
                prop { AG true }
            """,
        )

        assertThat(edges).containsExactly(
            startToRunning,
            UppaalEdge("running", "loc_0", guard = "(x < 3)"),
            UppaalEdge("loc_0", "running", assignment = "x = 1"),
            UppaalEdge("running", "loc_1", guard = "!((x < 3))"),
            UppaalEdge("loc_1", "running"),
        )
    }

    @Test
    suspend fun `an integer havoc is an edge per value the model compares against`() {
        val edges = edges(
            """
                var x : int := 0
                init { }
                tran { havoc(x) }
                prop { AG x < 5 }
            """,
        )

        assertThat(edges).containsExactly(
            startToRunning,
            UppaalEdge("running", "running", assignment = "x = 0"),
            UppaalEdge("running", "running", assignment = "x = 4"),
            UppaalEdge("running", "running", assignment = "x = 5"),
        )
    }

    @Test
    suspend fun `boolean and enum havocs select over the domain`() {
        val edges = edges(
            """
                var b : bool := false
                var c : test::colors::Color := test::colors::Color::Red
                init { }
                tran { havoc(b) havoc(c) }
                prop { AG true }
            """,
        )

        assertThat(edges).containsExactly(
            startToRunning,
            UppaalEdge("running", "loc_0", select = "havoc_b_0 : int[0, 1]", assignment = "b = havoc_b_0"),
            UppaalEdge("loc_0", "running", select = "havoc_c_1 : int[0, 1]", assignment = "c = havoc_c_1"),
        )
    }

    @Test
    suspend fun `a local variable is a global assigned by its declaration`() {
        val model = nta(
            """
                var x : int := 0
                init { }
                tran { var d : int := 2 x := x + d }
                prop { AG true }
            """,
        )

        val local = model.globalDeclarations.variables.last()
        assertThat(local.name).matches("d_\\d+")
        assertThat(local.initialValue).isNull()
        assertThat(model.templates.single().edges).containsExactly(
            startToRunning,
            UppaalEdge("running", "loc_0", assignment = "${local.name} = 2"),
            UppaalEdge("loc_0", "running", assignment = "x = (x + ${local.name})"),
        )
    }

    @Test
    suspend fun `operators render in Uppaal syntax`() {
        val edges = edges(
            """
                var x : int := 0
                var a : bool := false
                var b : bool := true
                init { }
                tran { choice {
                    x := ((x + 1) - (2 * x)) / 3
                } or {
                    a := (a && b) || !(a ^^ b)
                } or {
                    assume(x < 1 && x <= 2 && x > -3 && x >= -4 && x == 0 && x != 5)
                } or {
                    x := if x < 3 then x + 1 else 0
                } }
                prop { AG true }
            """,
        )

        assertThat(edges).containsExactly(
            startToRunning,
            UppaalEdge("running", "running", assignment = "x = (((x + 1) - (2 * x)) / 3)"),
            UppaalEdge("running", "running", assignment = "a = ((a && b) || !((a != b)))"),
            UppaalEdge("running", "running", guard = "((((((x < 1) && (x <= 2)) && (x > (-3))) && (x >= (-4))) && (x == 0)) && (x != 5))"),
            UppaalEdge("running", "running", assignment = "x = (((x < 3)) ? ((x + 1)) : (0))"),
        )
    }

    @Test
    suspend fun `init branches leave start and transition branches loop on running`() {
        val edges = edges(
            """
                var x : int := 0
                init { x := 1 } or { x := 2 }
                tran { x := 3 } or { x := 4 }
                prop { AG true }
            """,
        )

        assertThat(edges).containsExactly(
            UppaalEdge("start", "running", assignment = "x = 1"),
            UppaalEdge("start", "running", assignment = "x = 2"),
            UppaalEdge("running", "running", assignment = "x = 3"),
            UppaalEdge("running", "running", assignment = "x = 4"),
        )
    }

    @Test
    suspend fun `an AG property is an invariant over the running location`() {
        val query = query(
            """
                var x : int := 0
                init { }
                tran { }
                prop { AG x == 5 }
            """,
        )

        assertThat(query).isEqualTo("A[] (Model.Running imply ((x == 5)))")
    }

    @Test
    suspend fun `an EF property is a reachability query over the running location`() {
        val query = query(
            """
                var c : test::colors::Color := test::colors::Color::Red
                init { }
                tran { }
                prop { EF c == test::colors::Color::Green }
            """,
        )

        assertThat(query).isEqualTo("E<> (Model.Running and ((c == Green)))")
    }

    @Test
    suspend fun `a for operation is not supported`() {
        assertThrows<BackendUnsupportedException> {
            nta(
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
