/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.optimization.passes

import hu.bme.mit.semantifyr.compiler.pipeline.optimization.analyses.ReachingDefinitionsAnalysis
import org.junit.jupiter.api.Test

/**
 * Larger-fixture tests for [DeadStoreEliminationPass]. Each test is an
 * optimization step: input + expected output after DSE runs.
 */
class ComplexDeadStoreEliminationScenariosTest : PassTestBase() {

    @Test
    fun `write whose value is read by a later guard in the same tran is kept`() = assertPassTransforms(
        source = """
            inlined oxsts of semantifyr::Anything
            var a : int := 0
            init { }
            tran {
                a := 5
                assume(a == 5)
            }
            prop { AG true }
        """,
        expectedSource = """
            inlined oxsts of semantifyr::Anything
            var a : int := 0
            init { }
            tran {
                a := 5
                assume(a == 5)
            }
            prop { AG true }
        """,
        analysisClasses = listOf(ReachingDefinitionsAnalysis::class.java),
    ) { injector ->
        injector.getInstance(DeadStoreEliminationPass::class.java)
    }

    @Test
    fun `property read keeps every write to a property-relevant variable alive`() = assertPassTransforms(
        // Gamma pattern: property reads activeState, multiple branches write
        // it. All writes should be live (they're in the reaching-def set of
        // the property read).
        source = """
            inlined oxsts of semantifyr::Anything
            var state : int := -1
            init { state := 0 }
            tran {
                choice { state := 1 } or { state := 2 } or { state := 3 }
            }
            prop { AG (state != 42) }
        """,
        expectedSource = """
            inlined oxsts of semantifyr::Anything
            var state : int := -1
            init { state := 0 }
            tran {
                choice { state := 1 } or { state := 2 } or { state := 3 }
            }
            prop { AG (state != 42) }
        """,
        analysisClasses = listOf(ReachingDefinitionsAnalysis::class.java),
    ) { injector ->
        injector.getInstance(DeadStoreEliminationPass::class.java)
    }

    @Test
    fun `consecutive writes - the earlier write stays because it reaches via the loop back-edge`() = assertPassTransforms(
        // `state := -1; state := 25` - both writes appear in the reaching-def
        // set of a subsequent read at the start of the next iteration.
        // Conservatively, both are live.
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
        analysisClasses = listOf(ReachingDefinitionsAnalysis::class.java),
    ) { injector ->
        injector.getInstance(DeadStoreEliminationPass::class.java)
    }

    @Test
    fun `havoc followed by a deterministic write - both considered live`() = assertPassTransforms(
        // After havoc(x); x := 5; read x - the read sees only x := 5 within
        // the same tran. But the havoc is STILL live because it reaches the
        // loop back-edge entry of the NEXT iteration's reads.
        source = """
            inlined oxsts of semantifyr::Anything
            var a : int := 0
            init { }
            tran {
                havoc(a)
                a := 5
            }
            prop { AG (a == 5) }
        """,
        expectedSource = """
            inlined oxsts of semantifyr::Anything
            var a : int := 0
            init { }
            tran {
                havoc(a)
                a := 5
            }
            prop { AG (a == 5) }
        """,
        analysisClasses = listOf(ReachingDefinitionsAnalysis::class.java),
    ) { injector ->
        injector.getInstance(DeadStoreEliminationPass::class.java)
    }

    @Test
    fun `write to a completely unread variable is dead`() = assertPassTransforms(
        source = """
            inlined oxsts of semantifyr::Anything
            var a : int := 0
            var scratch : int := 0
            init { }
            tran {
                scratch := 123
                a := 5
            }
            prop { AG (a == 5) }
        """,
        // Nothing reads 'scratch' - its write has no one to reach.
        expectedSource = """
            inlined oxsts of semantifyr::Anything
            var a : int := 0
            var scratch : int := 0
            init { }
            tran {
                a := 5
            }
            prop { AG (a == 5) }
        """,
        analysisClasses = listOf(ReachingDefinitionsAnalysis::class.java),
    ) { injector ->
        injector.getInstance(DeadStoreEliminationPass::class.java)
    }
}
