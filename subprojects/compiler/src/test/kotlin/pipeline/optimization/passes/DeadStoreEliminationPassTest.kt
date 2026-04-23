/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.optimization.passes

import hu.bme.mit.semantifyr.compiler.pipeline.optimization.analyses.ReachingDefinitionsAnalysis
import org.junit.jupiter.api.Test

class DeadStoreEliminationPassTest : PassTestBase() {

    @Test
    fun `write that reaches the property read is kept`() = assertPassTransforms(
        source = """
            inlined oxsts of semantifyr::Anything
            var a : int := 0
            init { }
            tran { a := 1 }
            prop { AG (a == 1) }
        """,
        expectedSource = """
            inlined oxsts of semantifyr::Anything
            var a : int := 0
            init { }
            tran { a := 1 }
            prop { AG (a == 1) }
        """,
        analysisClasses = listOf(ReachingDefinitionsAnalysis::class.java),
    ) { injector ->
        injector.getInstance(DeadStoreEliminationPass::class.java)
    }

    @Test
    fun `write followed by a guard reading the variable is kept`() = assertPassTransforms(
        source = """
            inlined oxsts of semantifyr::Anything
            var a : int := 0
            init { }
            tran {
                a := 1
                assume(a == 1)
            }
            prop { AG true }
        """,
        expectedSource = """
            inlined oxsts of semantifyr::Anything
            var a : int := 0
            init { }
            tran {
                a := 1
                assume(a == 1)
            }
            prop { AG true }
        """,
        analysisClasses = listOf(ReachingDefinitionsAnalysis::class.java),
    ) { injector ->
        injector.getInstance(DeadStoreEliminationPass::class.java)
    }

    @Test
    fun `property read keeps every write to a property-relevant variable alive`() = assertPassTransforms(
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
