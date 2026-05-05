/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.optimization.analyses

import hu.bme.mit.semantifyr.compiler.pipeline.utils.eAllOfType
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AssignmentOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ElementReference
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ConeOfInfluenceAnalysisTest : AnalysisTestBase() {
    @Test
    fun `variable read in the property is marked relevant`() {
        val (inlined, result) = runConeOfInfluence(
            """
                inlined oxsts of semantifyr::Anything
                var a : int := 0
                init { }
                tran { }
                prop { AG (a == 1) }
            """,
        )

        val a = inlined.varNamed("a")
        assertThat(result.isRelevant(a)).isTrue
    }

    @Test
    fun `variable not read by the property is not relevant`() {
        val (inlined, result) = runConeOfInfluence(
            """
                inlined oxsts of semantifyr::Anything
                var a : int := 0
                var b : int := 0
                init { }
                tran { b := 2 }
                prop { AG (a == 1) }
            """,
        )

        val b = inlined.varNamed("b")
        assertThat(result.isRelevant(b)).isFalse
        val bAssignment = inlined.assignmentsTo(b).single()
        assertThat(result.isRelevant(bAssignment)).isFalse
    }

    @Test
    fun `every assignment to a relevant variable is relevant`() {
        val (inlined, result) = runConeOfInfluence(
            """
                inlined oxsts of semantifyr::Anything
                var a : int := 0
                init { a := 1 }
                tran {
                    a := 2
                    a := 3
                }
                prop { AG (a == 1) }
            """,
        )

        val a = inlined.varNamed("a")
        assertThat(result.isRelevant(a)).isTrue
        for (assignment in inlined.assignmentsTo(a)) {
            assertThat(result.isRelevant(assignment))
                .`as`("assignment $assignment to $a should be relevant")
                .isTrue
        }
    }

    @Test
    fun `sibling assume guard makes the guard variable relevant`() {
        val (inlined, result) = runConeOfInfluence(
            """
                inlined oxsts of semantifyr::Anything
                var a : int := 0
                var b : int := 0
                init { }
                tran {
                    choice {
                        assume(b == 0)
                        a := 1
                    } or {
                        assume(b != 0)
                        a := 2
                    }
                }
                prop { AG (a == 1) }
            """,
        )

        val a = inlined.varNamed("a")
        val b = inlined.varNamed("b")
        assertThat(result.isRelevant(a)).isTrue
        assertThat(result.isRelevant(b))
            .`as`("sibling-assume guard variable 'b' should be in the cone")
            .isTrue
    }

    @Test
    fun `transitive data dependence pulls the feeder variable into the cone`() {
        val (inlined, result) = runConeOfInfluence(
            """
                inlined oxsts of semantifyr::Anything
                var a : int := 0
                var b : int := 0
                init { }
                tran {
                    b := 5
                    a := b
                }
                prop { AG (a == 5) }
            """,
        )

        val a = inlined.varNamed("a")
        val b = inlined.varNamed("b")
        assertThat(result.isRelevant(a)).isTrue
        assertThat(result.isRelevant(b))
            .`as`("data-dep via 'a := b' should pull 'b' into the cone")
            .isTrue
        val bWrite = inlined.assignmentsTo(b).single()
        assertThat(result.isRelevant(bWrite))
            .`as`("assignment 'b := 5' should be relevant because 'b' is in the cone")
            .isTrue
    }

    @Test
    fun `consecutive writes inside one branch all stay relevant`() {
        val (inlined, result) = runConeOfInfluence(
            """
                inlined oxsts of semantifyr::Anything
                var activeState : int := -1
                init { activeState := 0 }
                tran {
                    choice {
                        assume(activeState == 0)
                        activeState := -1
                        activeState := 25
                    }
                }
                prop { AG (activeState != 25) }
            """,
        )

        val activeState = inlined.varNamed("activeState")
        assertThat(result.isRelevant(activeState)).isTrue
        val writes = inlined.assignmentsTo(activeState)
        assertThat(writes).hasSize(3) // init + two inside the choice branch
        for (write in writes) {
            assertThat(result.isRelevant(write))
                .`as`("every write to the property-relevant variable should stay relevant")
                .isTrue
        }
    }

    @Test
    fun `state-machine-style guarded write stays in the cone`() {
        val (inlined, result) = runConeOfInfluence(
            """
                inlined oxsts of semantifyr::Anything
                var activeState : int := -1
                var inputEvent : int := 0
                init { activeState := 0 }
                tran {
                    choice {
                        assume(activeState == 0 && inputEvent == 1)
                        activeState := 25
                    } or {
                        assume(activeState == 25 && inputEvent == 2)
                        activeState := 0
                    }
                }
                prop { AG (activeState != 25) }
            """,
        )

        val activeState = inlined.varNamed("activeState")
        val inputEvent = inlined.varNamed("inputEvent")
        assertThat(result.isRelevant(activeState)).isTrue
        assertThat(result.isRelevant(inputEvent))
            .`as`("the input-event variable guards relevant writes and must be in the cone")
            .isTrue
        for (write in inlined.assignmentsTo(activeState)) {
            assertThat(result.isRelevant(write))
                .`as`("every write to the property-relevant variable should stay relevant")
                .isTrue
        }
    }

    @Test
    fun `nested choice inside if inside choice propagates guards correctly`() {
        val (inlined, result) = runConeOfInfluence(
            """
                inlined oxsts of semantifyr::Anything
                var a : int := 0
                var b : int := 0
                var c : int := 0
                var d : int := 0
                init { }
                tran {
                    choice {
                        assume(b == 0)
                        if (c == 1) {
                            choice {
                                assume(d == 1)
                                a := 1
                            } or {
                                assume(d == 2)
                                a := 2
                            }
                        } else {
                            a := 3
                        }
                    } or {
                        assume(b != 0)
                        a := 4
                    }
                }
                prop { AG (a == 1) }
            """,
        )

        val a = inlined.varNamed("a")
        val b = inlined.varNamed("b")
        val c = inlined.varNamed("c")
        val d = inlined.varNamed("d")
        assertThat(result.isRelevant(a)).isTrue
        assertThat(result.isRelevant(b))
            .`as`("outer choice's assume guards ('b') must be in the cone")
            .isTrue
        assertThat(result.isRelevant(c))
            .`as`("nested if-guard ('c') must be in the cone")
            .isTrue
        assertThat(result.isRelevant(d))
            .`as`("deeply nested inner-choice assume guards ('d') must be in the cone under atomic commit")
            .isTrue
        for (write in inlined.assignmentsTo(a)) {
            assertThat(result.isRelevant(write))
                .`as`("every nested write to relevant var 'a' must stay relevant")
                .isTrue
        }
    }

    @Test
    fun `for-operation range read pulls the range variable into the cone`() {
        val (inlined, result) = runConeOfInfluence(
            """
                inlined oxsts of semantifyr::Anything
                var n : int := 5
                var sum : int := 0
                init { }
                tran {
                    for (x in 0 .. n) {
                        sum := sum + x
                    }
                }
                prop { AG (sum != 999) }
            """,
        )

        val n = inlined.varNamed("n")
        val sum = inlined.varNamed("sum")
        assertThat(result.isRelevant(sum)).isTrue
        assertThat(result.isRelevant(n))
            .`as`("the for-loop's range variable controls how many writes to 'sum' fire and must be in the cone")
            .isTrue
    }

    @Test
    fun `assume in tran keeps its read variables in the cone even when the property has no variables`() {
        val (inlined, result) = runConeOfInfluence(
            """
                inlined oxsts of semantifyr::Anything
                var step : int := -1
                init { step := 1 }
                tran {
                    assume(step == 1)
                    step := 2
                }
                prop { AG true }
            """,
        )

        val step = inlined.varNamed("step")
        assertThat(result.isRelevant(step))
            .`as`("step is read in a tran assume; it must stay in the cone even if the property is variable-free")
            .isTrue
        for (write in inlined.assignmentsTo(step)) {
            assertThat(result.isRelevant(write))
                .`as`("every write to the assume-read variable must stay in the cone")
                .isTrue
        }
    }

    @Test
    fun `assume in init keeps its read variables in the cone even when the property has no variables`() {
        val (inlined, result) = runConeOfInfluence(
            """
                inlined oxsts of semantifyr::Anything
                var seed : int := 7
                init {
                    assume(seed == 7)
                }
                tran { }
                prop { AG true }
            """,
        )

        val seed = inlined.varNamed("seed")
        assertThat(result.isRelevant(seed))
            .`as`("init-assume reads must seed the cone of influence")
            .isTrue
    }

    @Test
    fun `assume on x backward-constrains earlier writes to x - they must all stay in the cone`() {
        val (inlined, result) = runConeOfInfluence(
            """
                inlined oxsts of semantifyr::Anything
                var x : int := 0
                init {
                    x := 5
                }
                tran {
                    assume(x == 5)
                    x := 6
                }
                prop { AG true }
            """,
        )

        val x = inlined.varNamed("x")
        assertThat(result.isRelevant(x)).isTrue
        val writes = inlined.assignmentsTo(x)
        assertThat(writes).hasSize(2)
        for (write in writes) {
            assertThat(result.isRelevant(write))
                .`as`("write '${(write.expression)}' must stay - it sets the value the assume backward-constrains")
                .isTrue
        }
    }

    @Test
    fun `assume reading multiple variables seeds all of them into the cone`() {
        val (inlined, result) = runConeOfInfluence(
            """
                inlined oxsts of semantifyr::Anything
                var x : int := 0
                var y : int := 0
                init {
                    x := 1
                    y := 2
                }
                tran {
                    assume(x == 1 && y == 2)
                }
                prop { AG true }
            """,
        )

        val x = inlined.varNamed("x")
        val y = inlined.varNamed("y")
        assertThat(result.isRelevant(x))
            .`as`("'x' is read in the assume's first conjunct")
            .isTrue
        assertThat(result.isRelevant(y))
            .`as`("'y' is read in the assume's second conjunct")
            .isTrue
    }

    @Test
    fun `assume read variable transitively pulls in its data feeders even with empty property`() {
        val (inlined, result) = runConeOfInfluence(
            """
                inlined oxsts of semantifyr::Anything
                var available : int := 0
                var selected : int := 0
                init {
                    available := 7
                    selected := available
                }
                tran {
                    assume(selected == 7)
                }
                prop { AG true }
            """,
        )

        val available = inlined.varNamed("available")
        val selected = inlined.varNamed("selected")
        assertThat(result.isRelevant(selected)).isTrue
        assertThat(result.isRelevant(available))
            .`as`("'available' feeds 'selected' via assignment, so it is transitively relevant")
            .isTrue
    }

    @Test
    fun `outer assume guarding inner writes pulls its reads into the cone`() {
        val (inlined, result) = runConeOfInfluence(
            """
                inlined oxsts of semantifyr::Anything
                var a : int := 0
                var guard : int := 0
                init { }
                tran {
                    assume(guard == 1)
                    choice {
                        a := 1
                    } or {
                        a := 2
                    }
                }
                prop { AG (a == 1) }
            """,
        )

        val guard = inlined.varNamed("guard")
        assertThat(result.isRelevant(guard))
            .`as`("an outer assume that gates relevant inner writes must be in the cone")
            .isTrue
    }

    @Test
    fun `writes to irrelevant variables in a sibling branch stay out of the cone`() {
        val (inlined, result) = runConeOfInfluence(
            """
                inlined oxsts of semantifyr::Anything
                var a : int := 0
                var y : int := 0
                var siblingGuard : int := 0
                init { }
                tran {
                    choice {
                        a := 1
                    } or {
                        assume(siblingGuard == 7)
                        y := 5
                    }
                }
                prop { AG (a == 1) }
            """,
        )

        val y = inlined.varNamed("y")
        val siblingGuard = inlined.varNamed("siblingGuard")
        assertThat(result.isRelevant(y))
            .`as`("'y' is not read by the property and has no transitive dependence on it")
            .isFalse
        val yWrites = inlined.assignmentsTo(y)
        for (write in yWrites) {
            assertThat(result.isRelevant(write))
                .`as`("irrelevant 'y := 5' must not gate anything in the cone")
                .isFalse
        }

        assertThat(result.isRelevant(siblingGuard))
            .`as`("under atomic commit, sibling assume variables are control deps")
            .isTrue
    }

    private fun InlinedOxsts.varNamed(name: String): VariableDeclaration {
        return eAllOfType<VariableDeclaration>().firstOrNull {
            it.name == name
        } ?: error("No variable named '$name' in inlined oxsts")
    }

    private fun InlinedOxsts.assignmentsTo(variable: VariableDeclaration): List<AssignmentOperation> {
        return eAllOfType<AssignmentOperation>().filter {
            val ref = it.reference
            ref is ElementReference && ref.element === variable
        }.toList()
    }
}
