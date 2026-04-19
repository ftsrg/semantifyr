/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.optimization.passes

import hu.bme.mit.semantifyr.compiler.pipeline.optimization.analyses.ReachingDefinitionsAnalysis
import org.junit.jupiter.api.Test

/**
 * Larger-fixture tests for [CopyPropagationPass]. Each test declares what
 * should and should not be substituted.
 */
class ComplexCopyPropagationScenariosTest : PassTestBase() {

    @Test
    fun `single assignment chain - intra-tran propagation`() = assertPassTransforms(
        // a := 5 is the only reaching def for the read in b := a in the same
        // transition; the RHS is a literal; the pass substitutes b := 5.
        source = """
            inlined oxsts of semantifyr::Anything
            var a : int := 0
            var b : int := 0
            init { }
            tran {
                a := 5
                b := a
            }
            prop { AG true }
        """,
        expectedSource = """
            inlined oxsts of semantifyr::Anything
            var a : int := 0
            var b : int := 0
            init { }
            tran {
                a := 5
                b := 5
            }
            prop { AG true }
        """,
        analysisClasses = listOf(ReachingDefinitionsAnalysis::class.java),
    ) { injector ->
        injector.getInstance(CopyPropagationPass::class.java)
    }

    @Test
    fun `multiple reaching defs prevent propagation`() = assertPassTransforms(
        // Two choice branches each write a different value to a; the read of
        // a in b := a sees both defs, so the pass cannot substitute.
        source = """
            inlined oxsts of semantifyr::Anything
            var a : int := 0
            var b : int := 0
            init { }
            tran {
                choice { a := 1 } or { a := 2 }
                b := a
            }
            prop { AG true }
        """,
        expectedSource = """
            inlined oxsts of semantifyr::Anything
            var a : int := 0
            var b : int := 0
            init { }
            tran {
                choice { a := 1 } or { a := 2 }
                b := a
            }
            prop { AG true }
        """,
        analysisClasses = listOf(ReachingDefinitionsAnalysis::class.java),
    ) { injector ->
        injector.getInstance(CopyPropagationPass::class.java)
    }

    @Test
    fun `single init write is the unique reaching def for the property - fold to literal`() = assertPassTransforms(
        // The property is evaluated at post-init and post-main states. Main
        // does nothing, so 'a' always equals 1 at any state the property
        // observes. CopyPropagation correctly folds the read. The resulting
        // model may have no reachable reads of 'a' - that's a valid outcome.
        source = """
            inlined oxsts of semantifyr::Anything
            var a : int := 0
            init { a := 1 }
            tran { }
            prop { AG (a != 27) }
        """,
        expectedSource = """
            inlined oxsts of semantifyr::Anything
            var a : int := 0
            init { a := 1 }
            tran { }
            prop { AG (1 != 27) }
        """,
        analysisClasses = listOf(ReachingDefinitionsAnalysis::class.java),
    ) { injector ->
        injector.getInstance(CopyPropagationPass::class.java)
    }

    @Test
    fun `havoc blocks propagation - a havoced variable has no single reaching def`() = assertPassTransforms(
        source = """
            inlined oxsts of semantifyr::Anything
            var a : int := 0
            var b : int := 0
            init { }
            tran {
                havoc(a)
                b := a
            }
            prop { AG true }
        """,
        expectedSource = """
            inlined oxsts of semantifyr::Anything
            var a : int := 0
            var b : int := 0
            init { }
            tran {
                havoc(a)
                b := a
            }
            prop { AG true }
        """,
        analysisClasses = listOf(ReachingDefinitionsAnalysis::class.java),
    ) { injector ->
        injector.getInstance(CopyPropagationPass::class.java)
    }

    @Test
    fun `RHS that is not a literal or element reference is not propagated`() = assertPassTransforms(
        // The pass only copies literals and element references (no
        // arithmetic, no call suffix, etc). This prevents duplicating
        // expensive or aliased expressions.
        source = """
            inlined oxsts of semantifyr::Anything
            var a : int := 0
            var b : int := 0
            var c : int := 0
            init { }
            tran {
                a := b + 1
                c := a
            }
            prop { AG true }
        """,
        expectedSource = """
            inlined oxsts of semantifyr::Anything
            var a : int := 0
            var b : int := 0
            var c : int := 0
            init { }
            tran {
                a := b + 1
                c := a
            }
            prop { AG true }
        """,
        analysisClasses = listOf(ReachingDefinitionsAnalysis::class.java),
    ) { injector ->
        injector.getInstance(CopyPropagationPass::class.java)
    }

    @Test
    fun `self-read-same-variable check is narrow - transitive self-assignment is allowed`() = assertPassTransforms(
        // The pass only skips when the read variable and the RHS element
        // resolve to the same declaration. In a single pass, both reads get
        // substituted using the captured reaching-def map: the read of x in
        // `y := x` has initialIn[x] = {`x := y`} as its reaching def, so x
        // gets substituted with y, producing `y := y`. Similarly `x := y`
        // becomes `x := x`. Both are valid semantic no-ops at the sites.
        source = """
            inlined oxsts of semantifyr::Anything
            var x : int := 0
            var y : int := 0
            init { }
            tran {
                y := x
                x := y
            }
            prop { AG true }
        """,
        expectedSource = """
            inlined oxsts of semantifyr::Anything
            var x : int := 0
            var y : int := 0
            init { }
            tran {
                y := y
                x := x
            }
            prop { AG true }
        """,
        analysisClasses = listOf(ReachingDefinitionsAnalysis::class.java),
    ) { injector ->
        injector.getInstance(CopyPropagationPass::class.java)
    }

    @Test
    fun `chain of copies - single pass only folds via the stored reaching-def map`() = assertPassTransforms(
        // The pass iterates the reaching-def map captured at pass entry. A
        // read of b in `c := b` has the single reaching def `b := a`, whose
        // RHS is an element reference to a. Substituting the read of b with
        // a produces `c := a`. Folding c all the way to 5 requires the outer
        // fixpoint to re-run the pass after RD is recomputed.
        source = """
            inlined oxsts of semantifyr::Anything
            var a : int := 0
            var b : int := 0
            var c : int := 0
            init { }
            tran {
                a := 5
                b := a
                c := b
            }
            prop { AG true }
        """,
        expectedSource = """
            inlined oxsts of semantifyr::Anything
            var a : int := 0
            var b : int := 0
            var c : int := 0
            init { }
            tran {
                a := 5
                b := 5
                c := a
            }
            prop { AG true }
        """,
        analysisClasses = listOf(ReachingDefinitionsAnalysis::class.java),
    ) { injector ->
        injector.getInstance(CopyPropagationPass::class.java)
    }
}
