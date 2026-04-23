/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.optimization.passes

import hu.bme.mit.semantifyr.compiler.pipeline.optimization.analyses.ConeOfInfluenceAnalysis
import org.junit.jupiter.api.Test

class DeadCodeRemovalPassTest : PassTestBase() {

    @Test
    fun `assignment to a variable not read by the property is removed`() = assertPassTransforms(
        source = """
            inlined oxsts of semantifyr::Anything
            var a : int := 0
            var b : int := 0
            init { }
            tran {
                a := 1
                b := 2
            }
            prop { AG (a == 1) }
        """,
        expectedSource = """
            inlined oxsts of semantifyr::Anything
            var a : int := 0
            var b : int := 0
            init { }
            tran { a := 1 }
            prop { AG (a == 1) }
        """,
        analysisClasses = listOf(ConeOfInfluenceAnalysis::class.java),
    ) {
        it.getInstance(DeadCodeRemovalPass::class.java)
    }

    @Test
    fun `transitive dependency keeps the feeder assignment alive`() = assertPassTransforms(
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
        expectedSource = """
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
        analysisClasses = listOf(ConeOfInfluenceAnalysis::class.java),
    ) {
        it.getInstance(DeadCodeRemovalPass::class.java)
    }

    @Test
    fun `sibling assume guard keeps the gated write alive`() = assertPassTransforms(
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
        expectedSource = """
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
        analysisClasses = listOf(ConeOfInfluenceAnalysis::class.java),
    ) {
        it.getInstance(DeadCodeRemovalPass::class.java)
    }

    @Test
    fun `if-guarded write has its guard variable kept relevant`() = assertPassTransforms(
        source = """
            inlined oxsts of semantifyr::Anything
            var a : int := 0
            var b : int := 0
            init { }
            tran {
                if (b == 0) { a := 1 } else { a := 2 }
            }
            prop { AG (a == 1) }
        """,
        expectedSource = """
            inlined oxsts of semantifyr::Anything
            var a : int := 0
            var b : int := 0
            init { }
            tran {
                if (b == 0) { a := 1 } else { a := 2 }
            }
            prop { AG (a == 1) }
        """,
        analysisClasses = listOf(ConeOfInfluenceAnalysis::class.java),
    ) {
        it.getInstance(DeadCodeRemovalPass::class.java)
    }

    @Test
    fun `variable with both assignment and havoc keeps both when relevant`() = assertPassTransforms(
        source = """
            inlined oxsts of semantifyr::Anything
            var a : int := 0
            init { }
            tran {
                havoc(a)
                a := 1
            }
            prop { AG (a == 1) }
        """,
        expectedSource = """
            inlined oxsts of semantifyr::Anything
            var a : int := 0
            init { }
            tran {
                havoc(a)
                a := 1
            }
            prop { AG (a == 1) }
        """,
        analysisClasses = listOf(ConeOfInfluenceAnalysis::class.java),
    ) {
        it.getInstance(DeadCodeRemovalPass::class.java)
    }

    @Test
    fun `state machine - single region with event-driven transitions`() = assertPassTransforms(
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
    ) {
        it.getInstance(DeadCodeRemovalPass::class.java)
    }

    @Test
    fun `two independent state machines - writes to the unobserved region are pruned`() = assertPassTransforms(
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
    ) {
        it.getInstance(DeadCodeRemovalPass::class.java)
    }

    @Test
    fun `transitive relevance through a helper variable chain`() = assertPassTransforms(
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
    ) {
        it.getInstance(DeadCodeRemovalPass::class.java)
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
    ) {
        it.getInstance(DeadCodeRemovalPass::class.java)
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
    ) {
        it.getInstance(DeadCodeRemovalPass::class.java)
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
    ) {
        it.getInstance(DeadCodeRemovalPass::class.java)
    }

    @Test
    fun `kill-old-set-new pattern - both consecutive writes stay`() = assertPassTransforms(
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
    ) {
        it.getInstance(DeadCodeRemovalPass::class.java)
    }

    @Test
    fun `region marker local var keeps its writes and activates the guarded branches`() = assertPassTransforms(
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
    ) {
        it.getInstance(DeadCodeRemovalPass::class.java)
    }
}
