/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.optimization.passes

import hu.bme.mit.semantifyr.compiler.pipeline.optimization.analyses.ReachingDefinitionsAnalysis
import org.junit.jupiter.api.Test

class CopyPropagationPassTest : PassTestBase() {
    @Test
    fun `read with single reaching definition to a literal is substituted`() = assertPassTransforms(
        source = """
            inlined oxsts of semantifyr::Anything
            var a : int := 0
            var b : int := 0
            init { }
            tran {
                a := 7
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
                a := 7
                b := 7
            }
            prop { AG true }
        """,
        analysisClasses = listOf(ReachingDefinitionsAnalysis::class.java),
    ) {
        it.getInstance(CopyPropagationPass::class.java)
    }

    @Test
    fun `a single init write does not fold the property read since the property holds before init too`() = assertPassTransforms(
        source = """
            inlined oxsts of semantifyr::Anything
            var a : int := 0
            init { a := 1 }
            tran { }
            prop { AG (a == 1) }
        """,
        expectedSource = """
            inlined oxsts of semantifyr::Anything
            var a : int := 0
            init { a := 1 }
            tran { }
            prop { AG (a == 1) }
        """,
        analysisClasses = listOf(ReachingDefinitionsAnalysis::class.java),
    ) {
        it.getInstance(CopyPropagationPass::class.java)
    }

    @Test
    fun `multiple reaching defs prevent propagation`() = assertPassTransforms(
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
    ) {
        it.getInstance(CopyPropagationPass::class.java)
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
    ) {
        it.getInstance(CopyPropagationPass::class.java)
    }

    @Test
    fun `RHS that is not a literal or element reference is not propagated`() = assertPassTransforms(
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
    ) {
        it.getInstance(CopyPropagationPass::class.java)
    }

    @Test
    fun `self-read-same-variable check is narrow - transitive self-assignment is allowed`() = assertPassTransforms(
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
                y := x
                x := x
            }
            prop { AG true }
        """,
        analysisClasses = listOf(ReachingDefinitionsAnalysis::class.java),
    ) {
        it.getInstance(CopyPropagationPass::class.java)
    }

    @Test
    fun `read in init before its write does not see the later write as a reaching def`() = assertPassTransforms(
        source = """
            inlined oxsts of semantifyr::Anything
            var step : int := -1
            init {
                assume (step == -1)
                step := 0
            }
            tran { }
            prop { AG true }
        """,
        expectedSource = """
            inlined oxsts of semantifyr::Anything
            var step : int := -1
            init {
                assume (step == -1)
                step := 0
            }
            tran { }
            prop { AG true }
        """,
        analysisClasses = listOf(ReachingDefinitionsAnalysis::class.java),
    ) {
        it.getInstance(CopyPropagationPass::class.java)
    }

    @Test
    fun `tran write does not fold the property read when init does not overwrite the variable`() = assertPassTransforms(
        source = """
            inlined oxsts of semantifyr::Anything
            var done : bool := false
            init { }
            tran {
                done := true
            }
            prop { AG (done == true) }
        """,
        expectedSource = """
            inlined oxsts of semantifyr::Anything
            var done : bool := false
            init { }
            tran {
                done := true
            }
            prop { AG (done == true) }
        """,
        analysisClasses = listOf(ReachingDefinitionsAnalysis::class.java),
    ) {
        it.getInstance(CopyPropagationPass::class.java)
    }

    @Test
    fun `a single init write folds a tran read since init masks the initializer there`() = assertPassTransforms(
        source = """
            inlined oxsts of semantifyr::Anything
            var a : int := 0
            var b : int := 0
            init { a := 1 }
            tran {
                b := a
            }
            prop { AG (b != 99) }
        """,
        expectedSource = """
            inlined oxsts of semantifyr::Anything
            var a : int := 0
            var b : int := 0
            init { a := 1 }
            tran {
                b := 1
            }
            prop { AG (b != 99) }
        """,
        analysisClasses = listOf(ReachingDefinitionsAnalysis::class.java),
    ) {
        it.getInstance(CopyPropagationPass::class.java)
    }

    @Test
    fun `init choice with one writing and one empty branch leaves the initializer reachable`() = assertPassTransforms(
        source = """
            inlined oxsts of semantifyr::Anything
            var current : int := -1
            init {
                choice {
                    current := 0
                } or { }
            }
            tran { }
            prop { AG (current != 99) }
        """,
        expectedSource = """
            inlined oxsts of semantifyr::Anything
            var current : int := -1
            init {
                choice {
                    current := 0
                } or { }
            }
            tran { }
            prop { AG (current != 99) }
        """,
        analysisClasses = listOf(ReachingDefinitionsAnalysis::class.java),
    ) {
        it.getInstance(CopyPropagationPass::class.java)
    }

    @Test
    fun `init havoc is a must-write so a single tran write can fold`() = assertPassTransforms(
        source = """
            inlined oxsts of semantifyr::Anything
            var a : int := 0
            init { havoc(a) }
            tran { }
            prop { AG (a != 99) }
        """,
        expectedSource = """
            inlined oxsts of semantifyr::Anything
            var a : int := 0
            init { havoc(a) }
            tran { }
            prop { AG (a != 99) }
        """,
        analysisClasses = listOf(ReachingDefinitionsAnalysis::class.java),
    ) {
        it.getInstance(CopyPropagationPass::class.java)
    }

    @Test
    fun `chain of copies - single pass only folds via the stored reaching-def map`() = assertPassTransforms(
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
    ) {
        it.getInstance(CopyPropagationPass::class.java)
    }

    @Test
    fun `a copy is not propagated across a later write of its source`() = assertPassTransforms(
        source = """
            inlined oxsts of semantifyr::Anything
            var p : int := 0
            var q : int := 0
            init { }
            tran {
                q := p
                p := p + 1
                assume (q == 0)
            }
            prop { AG true }
        """,
        expectedSource = """
            inlined oxsts of semantifyr::Anything
            var p : int := 0
            var q : int := 0
            init { }
            tran {
                q := p
                p := p + 1
                assume (q == 0)
            }
            prop { AG true }
        """,
        analysisClasses = listOf(ReachingDefinitionsAnalysis::class.java),
    ) {
        it.getInstance(CopyPropagationPass::class.java)
    }

    @Test
    fun `a copy is not propagated when a branch between the copy and the read writes its source`() = assertPassTransforms(
        source = """
            inlined oxsts of semantifyr::Anything
            var p : int := 0
            var q : int := 0
            init { }
            tran {
                q := p
                choice { p := 1 } or { }
                assume (q == 0)
            }
            prop { AG true }
        """,
        expectedSource = """
            inlined oxsts of semantifyr::Anything
            var p : int := 0
            var q : int := 0
            init { }
            tran {
                q := p
                choice { p := 1 } or { }
                assume (q == 0)
            }
            prop { AG true }
        """,
        analysisClasses = listOf(ReachingDefinitionsAnalysis::class.java),
    ) {
        it.getInstance(CopyPropagationPass::class.java)
    }

    @Test
    fun `a copy is not propagated into a loop whose body writes its source`() = assertPassTransforms(
        source = """
            inlined oxsts of semantifyr::Anything
            var p : int := 0
            var q : int := 0
            init { }
            tran {
                q := p
                for (i in 0 .. 2) {
                    assume (q == 0)
                    p := p + 1
                }
            }
            prop { AG true }
        """,
        expectedSource = """
            inlined oxsts of semantifyr::Anything
            var p : int := 0
            var q : int := 0
            init { }
            tran {
                q := p
                for (i in 0 .. 2) {
                    assume (q == 0)
                    p := p + 1
                }
            }
            prop { AG true }
        """,
        analysisClasses = listOf(ReachingDefinitionsAnalysis::class.java),
    ) {
        it.getInstance(CopyPropagationPass::class.java)
    }

    @Test
    fun `a copy made in init is not propagated into tran when tran writes its source`() = assertPassTransforms(
        source = """
            inlined oxsts of semantifyr::Anything
            var p : int := 0
            var q : int := 0
            init { q := p }
            tran {
                p := p + 1
                assume (q == 0)
            }
            prop { AG true }
        """,
        expectedSource = """
            inlined oxsts of semantifyr::Anything
            var p : int := 0
            var q : int := 0
            init { q := p }
            tran {
                p := p + 1
                assume (q == 0)
            }
            prop { AG true }
        """,
        analysisClasses = listOf(ReachingDefinitionsAnalysis::class.java),
    ) {
        it.getInstance(CopyPropagationPass::class.java)
    }

    @Test
    fun `a copy is propagated when its source is written only after the read`() = assertPassTransforms(
        source = """
            inlined oxsts of semantifyr::Anything
            var p : int := 0
            var q : int := 0
            init { }
            tran {
                q := p
                assume (q == 0)
                p := p + 1
            }
            prop { AG true }
        """,
        expectedSource = """
            inlined oxsts of semantifyr::Anything
            var p : int := 0
            var q : int := 0
            init { }
            tran {
                q := p
                assume (p == 0)
                p := p + 1
            }
            prop { AG true }
        """,
        analysisClasses = listOf(ReachingDefinitionsAnalysis::class.java),
    ) {
        it.getInstance(CopyPropagationPass::class.java)
    }

    @Test
    fun `a copy made in init is propagated into tran when nothing writes its source after it`() = assertPassTransforms(
        source = """
            inlined oxsts of semantifyr::Anything
            var p : int := 0
            var q : int := 0
            init { q := p }
            tran {
                assume (q == 0)
            }
            prop { AG true }
        """,
        expectedSource = """
            inlined oxsts of semantifyr::Anything
            var p : int := 0
            var q : int := 0
            init { q := p }
            tran {
                assume (p == 0)
            }
            prop { AG true }
        """,
        analysisClasses = listOf(ReachingDefinitionsAnalysis::class.java),
    ) {
        it.getInstance(CopyPropagationPass::class.java)
    }
}
