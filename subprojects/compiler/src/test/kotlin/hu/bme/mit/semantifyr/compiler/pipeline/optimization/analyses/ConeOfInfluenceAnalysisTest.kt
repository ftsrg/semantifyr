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
                var observed : int := 0
                init {
                }
                tran {
                }
                prop {
                    AG (observed == 1)
                }
            """,
        )

        val observed = inlined.varNamed("observed")
        assertThat(result.isRelevant(observed)).isTrue
    }

    @Test
    fun `variable not read by the property is not relevant`() {
        val (inlined, result) = runConeOfInfluence(
            """
                inlined oxsts of semantifyr::Anything
                var observed : int := 0
                var unrelated : int := 0
                init {
                }
                tran {
                    unrelated := 2
                }
                prop {
                    AG (observed == 1)
                }
            """,
        )

        val unrelated = inlined.varNamed("unrelated")
        assertThat(result.isRelevant(unrelated)).isFalse
        val unrelatedAssignment = inlined.assignmentsTo(unrelated).single()
        assertThat(result.isRelevant(unrelatedAssignment)).isFalse
    }

    @Test
    fun `every assignment to a relevant variable is relevant`() {
        val (inlined, result) = runConeOfInfluence(
            """
                inlined oxsts of semantifyr::Anything
                var observed : int := 0
                init {
                    observed := 1
                }
                tran {
                    observed := 2
                    observed := 3
                }
                prop {
                    AG (observed == 1)
                }
            """,
        )

        val observed = inlined.varNamed("observed")
        assertThat(result.isRelevant(observed)).isTrue
        for (assignment in inlined.assignmentsTo(observed)) {
            assertThat(result.isRelevant(assignment))
                .`as`("assignment $assignment to $observed should be relevant")
                .isTrue
        }
    }

    @Test
    fun `sibling assume guard makes the guard variable relevant`() {
        val (inlined, result) = runConeOfInfluence(
            """
                inlined oxsts of semantifyr::Anything
                var observed : int := 0
                var guard : int := 0
                init {
                }
                tran {
                    choice {
                        assume(guard == 0)
                        observed := 1
                    } or {
                        assume(guard != 0)
                        observed := 2
                    }
                }
                prop {
                    AG (observed == 1)
                }
            """,
        )

        val observed = inlined.varNamed("observed")
        val guard = inlined.varNamed("guard")
        assertThat(result.isRelevant(observed)).isTrue
        assertThat(result.isRelevant(guard))
            .`as`("sibling-assume guard variable 'guard' should be in the cone")
            .isTrue
    }

    @Test
    fun `guard of an if enclosing an assumption is relevant`() {
        val (inlined, result) = runConeOfInfluence(
            """
                inlined oxsts of semantifyr::Anything
                var counter : int := 0
                var observed : int := 0
                init {
                }
                tran {
                    choice {
                        counter := counter + 1
                    } or {
                        if (counter <= 5) {
                            assume(false)
                        }
                        observed := 1
                    }
                }
                prop {
                    AG (observed == 0)
                }
            """,
        )

        val counter = inlined.varNamed("counter")
        assertThat(result.isRelevant(counter))
            .`as`("'counter' decides whether the assumption blocks the write of 'observed'")
            .isTrue
        assertThat(result.isRelevant(inlined.assignmentsTo(counter).single())).isTrue
    }

    @Test
    fun `transitive data dependence pulls the feeder variable into the cone`() {
        val (inlined, result) = runConeOfInfluence(
            """
                inlined oxsts of semantifyr::Anything
                var observed : int := 0
                var feeder : int := 0
                init {
                }
                tran {
                    feeder := 5
                    observed := feeder
                }
                prop {
                    AG (observed == 5)
                }
            """,
        )

        val observed = inlined.varNamed("observed")
        val feeder = inlined.varNamed("feeder")
        assertThat(result.isRelevant(observed)).isTrue
        assertThat(result.isRelevant(feeder))
            .`as`("data-dep via 'observed := feeder' should pull 'feeder' into the cone")
            .isTrue
        val feederWrite = inlined.assignmentsTo(feeder).single()
        assertThat(result.isRelevant(feederWrite))
            .`as`("assignment 'feeder := 5' should be relevant because 'feeder' is in the cone")
            .isTrue
    }

    @Test
    fun `consecutive writes inside one branch all stay relevant`() {
        val (inlined, result) = runConeOfInfluence(
            """
                inlined oxsts of semantifyr::Anything
                var activeState : int := -1
                init {
                    activeState := 0
                }
                tran {
                    choice {
                        assume(activeState == 0)
                        activeState := -1
                        activeState := 25
                    }
                }
                prop {
                    AG (activeState != 25)
                }
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
                init {
                    activeState := 0
                }
                tran {
                    choice {
                        assume(activeState == 0 && inputEvent == 1)
                        activeState := 25
                    } or {
                        assume(activeState == 25 && inputEvent == 2)
                        activeState := 0
                    }
                }
                prop {
                    AG (activeState != 25)
                }
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
                var observed : int := 0
                var outerGuard : int := 0
                var ifGuard : int := 0
                var innerGuard : int := 0
                init {
                }
                tran {
                    choice {
                        assume(outerGuard == 0)
                        if (ifGuard == 1) {
                            choice {
                                assume(innerGuard == 1)
                                observed := 1
                            } or {
                                assume(innerGuard == 2)
                                observed := 2
                            }
                        } else {
                            observed := 3
                        }
                    } or {
                        assume(outerGuard != 0)
                        observed := 4
                    }
                }
                prop {
                    AG (observed == 1)
                }
            """,
        )

        val observed = inlined.varNamed("observed")
        val outerGuard = inlined.varNamed("outerGuard")
        val ifGuard = inlined.varNamed("ifGuard")
        val innerGuard = inlined.varNamed("innerGuard")
        assertThat(result.isRelevant(observed)).isTrue
        assertThat(result.isRelevant(outerGuard))
            .`as`("outer choice's assume guards ('outerGuard') must be in the cone")
            .isTrue
        assertThat(result.isRelevant(ifGuard))
            .`as`("nested if-guard ('ifGuard') must be in the cone")
            .isTrue
        assertThat(result.isRelevant(innerGuard))
            .`as`("deeply nested inner-choice assume guards ('innerGuard') must be in the cone under atomic commit")
            .isTrue
        for (write in inlined.assignmentsTo(observed)) {
            assertThat(result.isRelevant(write))
                .`as`("every nested write to relevant var 'observed' must stay relevant")
                .isTrue
        }
    }

    @Test
    fun `for-operation range read pulls the range variable into the cone`() {
        val (inlined, result) = runConeOfInfluence(
            """
                inlined oxsts of semantifyr::Anything
                var bound : int := 5
                var sum : int := 0
                init {
                }
                tran {
                    for (index in 0..bound) {
                        sum := sum + index
                    }
                }
                prop {
                    AG (sum != 999)
                }
            """,
        )

        val bound = inlined.varNamed("bound")
        val sum = inlined.varNamed("sum")
        assertThat(result.isRelevant(sum)).isTrue
        assertThat(result.isRelevant(bound))
            .`as`("the for-loop's range variable controls how many writes to 'sum' fire and must be in the cone")
            .isTrue
    }

    @Test
    fun `assume in tran keeps its read variables in the cone even when the property has no variables`() {
        val (inlined, result) = runConeOfInfluence(
            """
                inlined oxsts of semantifyr::Anything
                var step : int := -1
                init {
                    step := 1
                }
                tran {
                    assume(step == 1)
                    step := 2
                }
                prop {
                    AG true
                }
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
                tran {
                }
                prop {
                    AG true
                }
            """,
        )

        val seed = inlined.varNamed("seed")
        assertThat(result.isRelevant(seed))
            .`as`("init-assume reads must seed the cone of influence")
            .isTrue
    }

    @Test
    fun `assume on value backward-constrains earlier writes to value - they must all stay in the cone`() {
        val (inlined, result) = runConeOfInfluence(
            """
                inlined oxsts of semantifyr::Anything
                var value : int := 0
                init {
                    value := 5
                }
                tran {
                    assume(value == 5)
                    value := 6
                }
                prop {
                    AG true
                }
            """,
        )

        val value = inlined.varNamed("value")
        assertThat(result.isRelevant(value)).isTrue
        val writes = inlined.assignmentsTo(value)
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
                var first : int := 0
                var second : int := 0
                init {
                    first := 1
                    second := 2
                }
                tran {
                    assume(first == 1 && second == 2)
                }
                prop {
                    AG true
                }
            """,
        )

        val first = inlined.varNamed("first")
        val second = inlined.varNamed("second")
        assertThat(result.isRelevant(first))
            .`as`("'first' is read in the assume's first conjunct")
            .isTrue
        assertThat(result.isRelevant(second))
            .`as`("'second' is read in the assume's second conjunct")
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
                prop {
                    AG true
                }
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
                var observed : int := 0
                var guard : int := 0
                init {
                }
                tran {
                    assume(guard == 1)
                    choice {
                        observed := 1
                    } or {
                        observed := 2
                    }
                }
                prop {
                    AG (observed == 1)
                }
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
                var observed : int := 0
                var unrelated : int := 0
                var siblingGuard : int := 0
                init {
                }
                tran {
                    choice {
                        observed := 1
                    } or {
                        assume(siblingGuard == 7)
                        unrelated := 5
                    }
                }
                prop {
                    AG (observed == 1)
                }
            """,
        )

        val unrelated = inlined.varNamed("unrelated")
        val siblingGuard = inlined.varNamed("siblingGuard")
        assertThat(result.isRelevant(unrelated))
            .`as`("'unrelated' is not read by the property and has no transitive dependence on it")
            .isFalse
        val unrelatedWrites = inlined.assignmentsTo(unrelated)
        for (write in unrelatedWrites) {
            assertThat(result.isRelevant(write))
                .`as`("irrelevant 'unrelated := 5' must not gate anything in the cone")
                .isFalse
        }

        assertThat(result.isRelevant(siblingGuard))
            .`as`("under atomic commit, sibling assume variables are control deps")
            .isTrue
    }

    @Test
    fun `a local initializer's reads are in the cone of whatever reads the local`() {
        val (inlined, result) = runConeOfInfluence(
            """
                inlined oxsts of semantifyr::Anything
                var toggled : int := 0
                var seen : int := 0
                init { }
                tran {
                    toggled := 1 - toggled
                    var local : int := toggled
                    seen := local
                }
                prop { EF (seen == 1) }
            """,
        )

        val toggled = inlined.varNamed("toggled")
        assertThat(result.isRelevant(toggled))
            .`as`("'toggled' feeds the local 'local' through its initializer, and 'local' feeds the property")
            .isTrue
        assertThat(result.isRelevant(inlined.assignmentsTo(toggled).single())).isTrue
    }

    @Test
    fun `a local initializer's reads stay out of the cone when nothing relevant reads the local`() {
        val (inlined, result) = runConeOfInfluence(
            """
                inlined oxsts of semantifyr::Anything
                var toggled : int := 0
                var seen : int := 0
                var other : int := 0
                init {
                }
                tran {
                    toggled := 1 - toggled
                    var local : int := toggled
                    other := local
                    seen := 1
                }
                prop {
                    EF (seen == 1)
                }
            """,
        )

        val toggled = inlined.varNamed("toggled")
        assertThat(result.isRelevant(toggled)).isFalse
        assertThat(result.isRelevant(inlined.assignmentsTo(toggled).single())).isFalse
    }

    private fun InlinedOxsts.varNamed(name: String): VariableDeclaration {
        return eAllOfType<VariableDeclaration>().firstOrNull {
            it.name == name
        } ?: error("No variable named '$name' in inlined oxsts")
    }

    private fun InlinedOxsts.assignmentsTo(variable: VariableDeclaration): List<AssignmentOperation> {
        return eAllOfType<AssignmentOperation>().filter {
            val reference = it.reference
            reference is ElementReference && reference.element === variable
        }.toList()
    }
}
