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
        val run = runAnalysis(
            source = """
                inlined oxsts of semantifyr::Anything
                var a : int := 0
                init { }
                tran { }
                prop { AG (a == 1) }
            """,
            analysisClass = ConeOfInfluenceAnalysis::class.java,
        )

        val a = run.inlinedOxsts.varNamed("a")
        assertThat(run.result.isRelevant(a)).isTrue
    }

    @Test
    fun `variable not read by the property is not relevant`() {
        val run = runAnalysis(
            source = """
                inlined oxsts of semantifyr::Anything
                var a : int := 0
                var b : int := 0
                init { }
                tran { b := 2 }
                prop { AG (a == 1) }
            """,
            analysisClass = ConeOfInfluenceAnalysis::class.java,
        )

        val b = run.inlinedOxsts.varNamed("b")
        assertThat(run.result.isRelevant(b)).isFalse
        val bAssignment = run.inlinedOxsts.assignmentsTo(b).single()
        assertThat(run.result.isRelevant(bAssignment)).isFalse
    }

    @Test
    fun `every assignment to a relevant variable is relevant`() {
        val run = runAnalysis(
            source = """
                inlined oxsts of semantifyr::Anything
                var a : int := 0
                init { a := 1 }
                tran {
                    a := 2
                    a := 3
                }
                prop { AG (a == 1) }
            """,
            analysisClass = ConeOfInfluenceAnalysis::class.java,
        )

        val a = run.inlinedOxsts.varNamed("a")
        assertThat(run.result.isRelevant(a)).isTrue
        for (assignment in run.inlinedOxsts.assignmentsTo(a)) {
            assertThat(run.result.isRelevant(assignment))
                .`as`("assignment $assignment to $a should be relevant")
                .isTrue
        }
    }

    @Test
    fun `sibling assume guard makes the guard variable relevant`() {
        val run = runAnalysis(
            source = """
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
            analysisClass = ConeOfInfluenceAnalysis::class.java,
        )

        val a = run.inlinedOxsts.varNamed("a")
        val b = run.inlinedOxsts.varNamed("b")
        assertThat(run.result.isRelevant(a)).isTrue
        assertThat(run.result.isRelevant(b))
            .`as`("sibling-assume guard variable 'b' should be in the cone")
            .isTrue
    }

    @Test
    fun `transitive data dependence pulls the feeder variable into the cone`() {
        val run = runAnalysis(
            source = """
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
            analysisClass = ConeOfInfluenceAnalysis::class.java,
        )

        val a = run.inlinedOxsts.varNamed("a")
        val b = run.inlinedOxsts.varNamed("b")
        assertThat(run.result.isRelevant(a)).isTrue
        assertThat(run.result.isRelevant(b))
            .`as`("data-dep via 'a := b' should pull 'b' into the cone")
            .isTrue
        val bWrite = run.inlinedOxsts.assignmentsTo(b).single()
        assertThat(run.result.isRelevant(bWrite))
            .`as`("assignment 'b := 5' should be relevant because 'b' is in the cone")
            .isTrue
    }

    // Gamma regression: a state variable that the property reads is written in
    // guarded branches. DeadCodeRemovalPass (driven by this analysis) was
    // erasing the writes, leaving the state machine stuck in its init state.
    @Test
    fun `consecutive writes inside one branch all stay relevant`() {
        // Gamma emits paired 'leave old state / enter new state' writes as
        // sequential assignments inside the same branch. Both must be in the cone.
        val run = runAnalysis(
            source = """
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
            analysisClass = ConeOfInfluenceAnalysis::class.java,
        )

        val activeState = run.inlinedOxsts.varNamed("activeState")
        assertThat(run.result.isRelevant(activeState)).isTrue
        val writes = run.inlinedOxsts.assignmentsTo(activeState)
        assertThat(writes).hasSize(3)  // init + two inside the choice branch
        for (write in writes) {
            assertThat(run.result.isRelevant(write))
                .`as`("every write to the property-relevant variable should stay relevant")
                .isTrue
        }
    }

    @Test
    fun `state-machine-style guarded write stays in the cone`() {
        val run = runAnalysis(
            source = """
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
            analysisClass = ConeOfInfluenceAnalysis::class.java,
        )

        val activeState = run.inlinedOxsts.varNamed("activeState")
        val inputEvent = run.inlinedOxsts.varNamed("inputEvent")
        assertThat(run.result.isRelevant(activeState)).isTrue
        assertThat(run.result.isRelevant(inputEvent))
            .`as`("the input-event variable guards relevant writes and must be in the cone")
            .isTrue
        for (write in run.inlinedOxsts.assignmentsTo(activeState)) {
            assertThat(run.result.isRelevant(write))
                .`as`("every write to the property-relevant variable should stay relevant")
                .isTrue
        }
    }

    private fun InlinedOxsts.varNamed(name: String): VariableDeclaration {
        return eAllOfType<VariableDeclaration>().firstOrNull { it.name == name }
            ?: error("No variable named '$name' in inlined oxsts")
    }

    private fun InlinedOxsts.assignmentsTo(variable: VariableDeclaration): List<AssignmentOperation> {
        return eAllOfType<AssignmentOperation>().filter { operation ->
            val ref = operation.reference
            ref is ElementReference && ref.element === variable
        }.toList()
    }
}
