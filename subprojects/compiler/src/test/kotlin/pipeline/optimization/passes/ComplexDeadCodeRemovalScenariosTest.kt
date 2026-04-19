/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.optimization.passes

import hu.bme.mit.semantifyr.compiler.pipeline.optimization.analyses.ConeOfInfluenceAnalysis
import org.junit.jupiter.api.Test

/**
 * Larger-fixture tests for [DeadCodeRemovalPass]. Each test is a concrete
 * scenario showing what I expect the pass to preserve vs drop. If any
 * "expected" snippet is wrong against OXSTS semantics, the pass (or the
 * analysis behind it) is buggy in that exact shape.
 */
class ComplexDeadCodeRemovalScenariosTest : PassTestBase() {

    @Test
    fun `state machine - single region with event-driven transitions`() = assertPassTransforms(
        // Station state machine: state 0 (Idle) -> state 25 (Operation) on
        // event 1, and back on event 2. Property checks Operation reachable.
        // Every write to activeState is in the cone because the property
        // reads activeState; inputEvent enters the cone because it guards
        // every write; the havoc of inputEvent is also relevant.
        source = """
            inlined oxsts of semantifyr::Anything
            var activeState : int := -1
            var inputEvent : int := 0
            init { activeState := 0 }
            tran {
                havoc(inputEvent)
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
        expectedSource = """
            inlined oxsts of semantifyr::Anything
            var activeState : int := -1
            var inputEvent : int := 0
            init { activeState := 0 }
            tran {
                havoc(inputEvent)
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
        analysisClasses = listOf(ConeOfInfluenceAnalysis::class.java),
    ) { injector ->
        injector.getInstance(DeadCodeRemovalPass::class.java)
    }

    @Test
    fun `two independent state machines - writes to both stay`() = assertPassTransforms(
        // Gamma-style: two regions progress in parallel. Property reads only
        // region A's state, but region B's state might guard A's writes (it
        // doesn't here) - so region B's writes can be pruned.
        source = """
            inlined oxsts of semantifyr::Anything
            var stateA : int := 0
            var stateB : int := 0
            var event : int := 0
            init { }
            tran {
                havoc(event)
                choice {
                    assume(event == 1)
                    stateA := 1
                } or {
                    assume(event == 2)
                    stateB := 1
                }
            }
            prop { AG (stateA != 1) }
        """,
        // stateB is NOT read by the property, so its write is dropped. event
        // is only relevant for the kept branch's guard.
        expectedSource = """
            inlined oxsts of semantifyr::Anything
            var stateA : int := 0
            var stateB : int := 0
            var event : int := 0
            init { }
            tran {
                havoc(event)
                choice {
                    assume(event == 1)
                    stateA := 1
                } or {
                    assume(event == 2)
                }
            }
            prop { AG (stateA != 1) }
        """,
        analysisClasses = listOf(ConeOfInfluenceAnalysis::class.java),
    ) { injector ->
        injector.getInstance(DeadCodeRemovalPass::class.java)
    }

    @Test
    fun `transitive relevance through a helper variable`() = assertPassTransforms(
        // b feeds a (a := b), property reads a. Writes to b are relevant via
        // data dependence; guards on b are also relevant.
        source = """
            inlined oxsts of semantifyr::Anything
            var a : int := 0
            var b : int := 0
            var c : int := 0
            init { }
            tran {
                b := c
                a := b
                c := 99
            }
            prop { AG (a != 99) }
        """,
        // c is relevant because b := c pulls it in; c's writes stay.
        expectedSource = """
            inlined oxsts of semantifyr::Anything
            var a : int := 0
            var b : int := 0
            var c : int := 0
            init { }
            tran {
                b := c
                a := b
                c := 99
            }
            prop { AG (a != 99) }
        """,
        analysisClasses = listOf(ConeOfInfluenceAnalysis::class.java),
    ) { injector ->
        injector.getInstance(DeadCodeRemovalPass::class.java)
    }

    @Test
    fun `deeply nested guards - all guard variables enter the cone`() = assertPassTransforms(
        source = """
            inlined oxsts of semantifyr::Anything
            var state : int := 0
            var g1 : int := 0
            var g2 : int := 0
            var g3 : int := 0
            init { }
            tran {
                if (g1 == 0) {
                    if (g2 == 1) {
                        if (g3 == 2) {
                            state := 42
                        }
                    }
                }
            }
            prop { AG (state != 42) }
        """,
        expectedSource = """
            inlined oxsts of semantifyr::Anything
            var state : int := 0
            var g1 : int := 0
            var g2 : int := 0
            var g3 : int := 0
            init { }
            tran {
                if (g1 == 0) {
                    if (g2 == 1) {
                        if (g3 == 2) {
                            state := 42
                        }
                    }
                }
            }
            prop { AG (state != 42) }
        """,
        analysisClasses = listOf(ConeOfInfluenceAnalysis::class.java),
    ) { injector ->
        injector.getInstance(DeadCodeRemovalPass::class.java)
    }

    @Test
    fun `havoc on a property-relevant variable is kept`() = assertPassTransforms(
        source = """
            inlined oxsts of semantifyr::Anything
            var a : int := 0
            init { }
            tran { havoc(a) }
            prop { AG (a != 42) }
        """,
        expectedSource = """
            inlined oxsts of semantifyr::Anything
            var a : int := 0
            init { }
            tran { havoc(a) }
            prop { AG (a != 42) }
        """,
        analysisClasses = listOf(ConeOfInfluenceAnalysis::class.java),
    ) { injector ->
        injector.getInstance(DeadCodeRemovalPass::class.java)
    }

    @Test
    fun `havoc on an unread variable is dropped`() = assertPassTransforms(
        source = """
            inlined oxsts of semantifyr::Anything
            var a : int := 0
            var b : int := 0
            init { }
            tran {
                havoc(b)
                a := 5
            }
            prop { AG (a != 5) }
        """,
        expectedSource = """
            inlined oxsts of semantifyr::Anything
            var a : int := 0
            var b : int := 0
            init { }
            tran {
                a := 5
            }
            prop { AG (a != 5) }
        """,
        analysisClasses = listOf(ConeOfInfluenceAnalysis::class.java),
    ) { injector ->
        injector.getInstance(DeadCodeRemovalPass::class.java)
    }

    @Test
    fun `three-state machine - all transitions stay`() = assertPassTransforms(
        // State 0 -> 1 -> 2 -> 0. Property: can we reach state 2?
        source = """
            inlined oxsts of semantifyr::Anything
            var state : int := -1
            var ev : int := 0
            init { state := 0 }
            tran {
                havoc(ev)
                choice {
                    assume(state == 0 && ev == 1)
                    state := 1
                } or {
                    assume(state == 1 && ev == 2)
                    state := 2
                } or {
                    assume(state == 2 && ev == 3)
                    state := 0
                }
            }
            prop { AG (state != 2) }
        """,
        expectedSource = """
            inlined oxsts of semantifyr::Anything
            var state : int := -1
            var ev : int := 0
            init { state := 0 }
            tran {
                havoc(ev)
                choice {
                    assume(state == 0 && ev == 1)
                    state := 1
                } or {
                    assume(state == 1 && ev == 2)
                    state := 2
                } or {
                    assume(state == 2 && ev == 3)
                    state := 0
                }
            }
            prop { AG (state != 2) }
        """,
        analysisClasses = listOf(ConeOfInfluenceAnalysis::class.java),
    ) { injector ->
        injector.getInstance(DeadCodeRemovalPass::class.java)
    }

    @Test
    fun `kill-old-set-new pattern - both consecutive writes stay`() = assertPassTransforms(
        // Gamma commonly emits a leave-state write followed by an enter-state
        // write. Both must stay relevant because either can be observed by
        // the property across iterations.
        source = """
            inlined oxsts of semantifyr::Anything
            var state : int := -1
            init { state := 0 }
            tran {
                assume(state == 0)
                state := -1
                state := 25
            }
            prop { AG (state != 25) }
        """,
        expectedSource = """
            inlined oxsts of semantifyr::Anything
            var state : int := -1
            init { state := 0 }
            tran {
                assume(state == 0)
                state := -1
                state := 25
            }
            prop { AG (state != 25) }
        """,
        analysisClasses = listOf(ConeOfInfluenceAnalysis::class.java),
    ) { injector ->
        injector.getInstance(DeadCodeRemovalPass::class.java)
    }

    @Test
    fun `region marker local var keeps its writes and activates the guarded branches`() = assertPassTransforms(
        // Gamma uses an `anyRegionExecuted` local flag: only commit the outer
        // block if at least one region's inner branch fired. The flag's
        // writes must stay (or the trailing assume always sees false and the
        // whole block rolls back).
        source = """
            inlined oxsts of semantifyr::Anything
            var state : int := 0
            init { }
            tran {
                var anyRegionExecuted : bool := false
                choice {
                    assume(state == 0)
                    state := 1
                    anyRegionExecuted := true
                } or {
                    assume(state == 1)
                    state := 0
                    anyRegionExecuted := true
                }
                assume(anyRegionExecuted)
            }
            prop { AG (state != 1) }
        """,
        expectedSource = """
            inlined oxsts of semantifyr::Anything
            var state : int := 0
            init { }
            tran {
                var anyRegionExecuted : bool := false
                choice {
                    assume(state == 0)
                    state := 1
                    anyRegionExecuted := true
                } or {
                    assume(state == 1)
                    state := 0
                    anyRegionExecuted := true
                }
                assume(anyRegionExecuted)
            }
            prop { AG (state != 1) }
        """,
        analysisClasses = listOf(ConeOfInfluenceAnalysis::class.java),
    ) { injector ->
        injector.getInstance(DeadCodeRemovalPass::class.java)
    }
}
