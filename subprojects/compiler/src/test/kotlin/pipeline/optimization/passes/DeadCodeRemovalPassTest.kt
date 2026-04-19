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
    ) { injector ->
        injector.getInstance(DeadCodeRemovalPass::class.java)
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
    ) { injector ->
        injector.getInstance(DeadCodeRemovalPass::class.java)
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
    ) { injector ->
        injector.getInstance(DeadCodeRemovalPass::class.java)
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
    ) { injector ->
        injector.getInstance(DeadCodeRemovalPass::class.java)
    }

    // Regression: ConeOfInfluenceAnalysis used to merge assignment and havoc
    // maps via Map.plus, which silently drops one group when a variable has both.
    // The fix concatenates writes before grouping; this test guards it.
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
    ) { injector ->
        injector.getInstance(DeadCodeRemovalPass::class.java)
    }
}
