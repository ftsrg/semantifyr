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
    fun `initializer plus a single tran write folds the property read to the init value`() = assertPassTransforms(
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
    fun `init choice that writes on every branch counts as must-write so the initializer is masked`() = assertPassTransforms(
        source = """
            inlined oxsts of semantifyr::Anything
            var current : int := -1
            init {
                choice {
                    current := 0
                } or {
                    current := 1
                }
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
                } or {
                    current := 1
                }
            }
            tran { }
            prop { AG (current != 99) }
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
}
