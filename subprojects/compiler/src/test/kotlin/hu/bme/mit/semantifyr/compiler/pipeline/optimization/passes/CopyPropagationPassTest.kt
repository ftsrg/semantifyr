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
            var source: int := 0
            var copy: int := 0
            init {
            }
            tran {
                source := 7
                copy := source
            }
            prop {
                AG true
            }
        """,
        expectedSource = """
            inlined oxsts of semantifyr::Anything
            var source: int := 0
            var copy: int := 0
            init {
            }
            tran {
                source := 7
                copy := 7
            }
            prop {
                AG true
            }
        """,
        analysisClasses = listOf(ReachingDefinitionsAnalysis::class.java),
    ) {
        it.getInstance(CopyPropagationPass::class.java)
    }

    @Test
    fun `a single init write folds the property read since the declaration valuation is not a state`() = assertPassTransforms(
        source = """
            inlined oxsts of semantifyr::Anything
            var value: int := 0
            init {
                value := 1
            }
            tran {
            }
            prop {
                AG (value == 1)
            }
        """,
        expectedSource = """
            inlined oxsts of semantifyr::Anything
            var value: int := 0
            init {
                value := 1
            }
            tran {
            }
            prop {
                AG (1 == 1)
            }
        """,
        analysisClasses = listOf(ReachingDefinitionsAnalysis::class.java),
    ) {
        it.getInstance(CopyPropagationPass::class.java)
    }

    @Test
    fun `multiple reaching definitions prevent propagation`() = assertPassTransforms(
        source = """
            inlined oxsts of semantifyr::Anything
            var source: int := 0
            var copy: int := 0
            init {
            }
            tran {
                choice {
                    source := 1
                } or {
                    source := 2
                }
                copy := source
            }
            prop {
                AG true
            }
        """,
        expectedSource = """
            inlined oxsts of semantifyr::Anything
            var source: int := 0
            var copy: int := 0
            init {
            }
            tran {
                choice {
                    source := 1
                } or {
                    source := 2
                }
                copy := source
            }
            prop {
                AG true
            }
        """,
        analysisClasses = listOf(ReachingDefinitionsAnalysis::class.java),
    ) {
        it.getInstance(CopyPropagationPass::class.java)
    }

    @Test
    fun `havoc blocks propagation - a havoced variable has no single reaching definition`() = assertPassTransforms(
        source = """
            inlined oxsts of semantifyr::Anything
            var source: int := 0
            var copy: int := 0
            init {
            }
            tran {
                havoc(source)
                copy := source
            }
            prop {
                AG true
            }
        """,
        expectedSource = """
            inlined oxsts of semantifyr::Anything
            var source: int := 0
            var copy: int := 0
            init {
            }
            tran {
                havoc(source)
                copy := source
            }
            prop {
                AG true
            }
        """,
        analysisClasses = listOf(ReachingDefinitionsAnalysis::class.java),
    ) {
        it.getInstance(CopyPropagationPass::class.java)
    }

    @Test
    fun `Right-hand side that is not a literal or element reference is not propagated`() = assertPassTransforms(
        source = """
            inlined oxsts of semantifyr::Anything
            var sum: int := 0
            var operand: int := 0
            var copy: int := 0
            init {
            }
            tran {
                sum := operand + 1
                copy := sum
            }
            prop {
                AG true
            }
        """,
        expectedSource = """
            inlined oxsts of semantifyr::Anything
            var sum: int := 0
            var operand: int := 0
            var copy: int := 0
            init {
            }
            tran {
                sum := operand + 1
                copy := sum
            }
            prop {
                AG true
            }
        """,
        analysisClasses = listOf(ReachingDefinitionsAnalysis::class.java),
    ) {
        it.getInstance(CopyPropagationPass::class.java)
    }

    @Test
    fun `self-read-same-variable check is narrow - transitive self-assignment is allowed`() = assertPassTransforms(
        source = """
            inlined oxsts of semantifyr::Anything
            var left: int := 0
            var right: int := 0
            init {
            }
            tran {
                right := left
                left := right
            }
            prop {
                AG true
            }
        """,
        expectedSource = """
            inlined oxsts of semantifyr::Anything
            var left: int := 0
            var right: int := 0
            init {
            }
            tran {
                right := left
                left := left
            }
            prop {
                AG true
            }
        """,
        analysisClasses = listOf(ReachingDefinitionsAnalysis::class.java),
    ) {
        it.getInstance(CopyPropagationPass::class.java)
    }

    @Test
    fun `read in init before its write does not see the later write as a reaching definition`() = assertPassTransforms(
        source = """
            inlined oxsts of semantifyr::Anything
            var step: int := 0
            init {
                assume(step == 0)
                step := 1
            }
            tran {
            }
            prop {
                AG true
            }
        """,
        expectedSource = """
            inlined oxsts of semantifyr::Anything
            var step: int := 0
            init {
                assume(step == 0)
                step := 1
            }
            tran {
            }
            prop {
                AG true
            }
        """,
        analysisClasses = listOf(ReachingDefinitionsAnalysis::class.java),
    ) {
        it.getInstance(CopyPropagationPass::class.java)
    }

    @Test
    fun `tran write does not fold the property read when init does not overwrite the variable`() = assertPassTransforms(
        source = """
            inlined oxsts of semantifyr::Anything
            var done: bool := false
            init {
            }
            tran {
                done := true
            }
            prop {
                AG (done == true)
            }
        """,
        expectedSource = """
            inlined oxsts of semantifyr::Anything
            var done: bool := false
            init {
            }
            tran {
                done := true
            }
            prop {
                AG (done == true)
            }
        """,
        analysisClasses = listOf(ReachingDefinitionsAnalysis::class.java),
    ) {
        it.getInstance(CopyPropagationPass::class.java)
    }

    @Test
    fun `a single init write folds a tran read since init masks the initializer there`() = assertPassTransforms(
        source = """
            inlined oxsts of semantifyr::Anything
            var source: int := 0
            var copy: int := 0
            init {
                source := 1
            }
            tran {
                copy := source
            }
            prop {
                AG (copy != 99)
            }
        """,
        expectedSource = """
            inlined oxsts of semantifyr::Anything
            var source: int := 0
            var copy: int := 0
            init {
                source := 1
            }
            tran {
                copy := 1
            }
            prop {
                AG (copy != 99)
            }
        """,
        analysisClasses = listOf(ReachingDefinitionsAnalysis::class.java),
    ) {
        it.getInstance(CopyPropagationPass::class.java)
    }

    @Test
    fun `init choice with one writing and one empty branch leaves the initializer reachable`() = assertPassTransforms(
        source = """
            inlined oxsts of semantifyr::Anything
            var current: int := -1
            init {
                choice {
                    current := 0
                } or {
                }
            }
            tran {
            }
            prop {
                AG (current != 99)
            }
        """,
        expectedSource = """
            inlined oxsts of semantifyr::Anything
            var current: int := -1
            init {
                choice {
                    current := 0
                } or {
                }
            }
            tran {
            }
            prop {
                AG (current != 99)
            }
        """,
        analysisClasses = listOf(ReachingDefinitionsAnalysis::class.java),
    ) {
        it.getInstance(CopyPropagationPass::class.java)
    }

    @Test
    fun `init havoc is a must-write so a single tran write can fold`() = assertPassTransforms(
        source = """
            inlined oxsts of semantifyr::Anything
            var value: int := 0
            init {
                havoc(value)
            }
            tran {
            }
            prop {
                AG (value != 99)
            }
        """,
        expectedSource = """
            inlined oxsts of semantifyr::Anything
            var value: int := 0
            init {
                havoc(value)
            }
            tran {
            }
            prop {
                AG (value != 99)
            }
        """,
        analysisClasses = listOf(ReachingDefinitionsAnalysis::class.java),
    ) {
        it.getInstance(CopyPropagationPass::class.java)
    }

    @Test
    fun `chain of copies - single pass only folds via the stored reaching-definition map`() = assertPassTransforms(
        source = """
            inlined oxsts of semantifyr::Anything
            var head: int := 0
            var middle: int := 0
            var tail: int := 0
            init {
            }
            tran {
                head := 5
                middle := head
                tail := middle
            }
            prop {
                AG true
            }
        """,
        expectedSource = """
            inlined oxsts of semantifyr::Anything
            var head: int := 0
            var middle: int := 0
            var tail: int := 0
            init {
            }
            tran {
                head := 5
                middle := 5
                tail := head
            }
            prop {
                AG true
            }
        """,
        analysisClasses = listOf(ReachingDefinitionsAnalysis::class.java),
    ) {
        it.getInstance(CopyPropagationPass::class.java)
    }

    @Test
    fun `a copy is not propagated across a later write of its source`() = assertPassTransforms(
        source = """
            inlined oxsts of semantifyr::Anything
            var source: int := 0
            var copy: int := 0
            init {
            }
            tran {
                copy := source
                source := source + 1
                assume(copy == 0)
            }
            prop {
                AG true
            }
        """,
        expectedSource = """
            inlined oxsts of semantifyr::Anything
            var source: int := 0
            var copy: int := 0
            init {
            }
            tran {
                copy := source
                source := source + 1
                assume(copy == 0)
            }
            prop {
                AG true
            }
        """,
        analysisClasses = listOf(ReachingDefinitionsAnalysis::class.java),
    ) {
        it.getInstance(CopyPropagationPass::class.java)
    }

    @Test
    fun `a copy is not propagated when a branch between the copy and the read writes its source`() = assertPassTransforms(
        source = """
            inlined oxsts of semantifyr::Anything
            var source: int := 0
            var copy: int := 0
            init {
            }
            tran {
                copy := source
                choice {
                    source := 1
                } or {
                }
                assume(copy == 0)
            }
            prop {
                AG true
            }
        """,
        expectedSource = """
            inlined oxsts of semantifyr::Anything
            var source: int := 0
            var copy: int := 0
            init {
            }
            tran {
                copy := source
                choice {
                    source := 1
                } or {
                }
                assume(copy == 0)
            }
            prop {
                AG true
            }
        """,
        analysisClasses = listOf(ReachingDefinitionsAnalysis::class.java),
    ) {
        it.getInstance(CopyPropagationPass::class.java)
    }

    @Test
    fun `a copy is not propagated into a loop whose body writes its source`() = assertPassTransforms(
        source = """
            inlined oxsts of semantifyr::Anything
            var source: int := 0
            var copy: int := 0
            init {
            }
            tran {
                copy := source
                for (index in 0..2) {
                    assume(copy == 0)
                    source := source + 1
                }
            }
            prop {
                AG true
            }
        """,
        expectedSource = """
            inlined oxsts of semantifyr::Anything
            var source: int := 0
            var copy: int := 0
            init {
            }
            tran {
                copy := source
                for (index in 0..2) {
                    assume(copy == 0)
                    source := source + 1
                }
            }
            prop {
                AG true
            }
        """,
        analysisClasses = listOf(ReachingDefinitionsAnalysis::class.java),
    ) {
        it.getInstance(CopyPropagationPass::class.java)
    }

    @Test
    fun `a copy made in init is not propagated into tran when tran writes its source`() = assertPassTransforms(
        source = """
            inlined oxsts of semantifyr::Anything
            var source: int := 0
            var copy: int := 0
            init {
                copy := source
            }
            tran {
                source := source + 1
                assume(copy == 0)
            }
            prop {
                AG true
            }
        """,
        expectedSource = """
            inlined oxsts of semantifyr::Anything
            var source: int := 0
            var copy: int := 0
            init {
                copy := source
            }
            tran {
                source := source + 1
                assume(copy == 0)
            }
            prop {
                AG true
            }
        """,
        analysisClasses = listOf(ReachingDefinitionsAnalysis::class.java),
    ) {
        it.getInstance(CopyPropagationPass::class.java)
    }

    @Test
    fun `a copy is propagated when its source is written only after the read`() = assertPassTransforms(
        source = """
            inlined oxsts of semantifyr::Anything
            var source: int := 0
            var copy: int := 0
            init {
            }
            tran {
                copy := source
                assume(copy == 0)
                source := source + 1
            }
            prop {
                AG true
            }
        """,
        expectedSource = """
            inlined oxsts of semantifyr::Anything
            var source: int := 0
            var copy: int := 0
            init {
            }
            tran {
                copy := source
                assume(source == 0)
                source := source + 1
            }
            prop {
                AG true
            }
        """,
        analysisClasses = listOf(ReachingDefinitionsAnalysis::class.java),
    ) {
        it.getInstance(CopyPropagationPass::class.java)
    }

    @Test
    fun `a copy made in init is propagated into tran when nothing writes its source after it`() = assertPassTransforms(
        source = """
            inlined oxsts of semantifyr::Anything
            var source: int := 0
            var copy: int := 0
            init {
                copy := source
            }
            tran {
                assume(copy == 0)
            }
            prop {
                AG true
            }
        """,
        expectedSource = """
            inlined oxsts of semantifyr::Anything
            var source: int := 0
            var copy: int := 0
            init {
                copy := source
            }
            tran {
                assume(source == 0)
            }
            prop {
                AG true
            }
        """,
        analysisClasses = listOf(ReachingDefinitionsAnalysis::class.java),
    ) {
        it.getInstance(CopyPropagationPass::class.java)
    }

    @Test
    fun `an init write on one branch is not propagated to a later init read`() = assertPassTransforms(
        source = """
            inlined oxsts of semantifyr::Anything
            var condition: bool := false
            var source: int := 0
            var copy: int := 9
            init {
                if (condition) {
                    source := 5
                }
                copy := source
            }
            tran {
            }
            prop {
                AG true
            }
        """,
        expectedSource = """
            inlined oxsts of semantifyr::Anything
            var condition: bool := false
            var source: int := 0
            var copy: int := 9
            init {
                if (condition) {
                    source := 5
                }
                copy := source
            }
            tran {
            }
            prop {
                AG true
            }
        """,
        analysisClasses = listOf(ReachingDefinitionsAnalysis::class.java),
    ) {
        it.getInstance(CopyPropagationPass::class.java)
    }
}
