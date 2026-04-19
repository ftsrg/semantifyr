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
        // Inside the transition, the `a` read in `b := a` has a unique
        // reaching definition (`a := 7` earlier in the same sequence) and
        // that RHS is a literal, so it is substituted.
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
    ) { injector ->
        injector.getInstance(CopyPropagationPass::class.java)
    }

    @Test
    fun `initializer plus a single tran write folds the property read to the init value`() = assertPassTransforms(
        // The property is evaluated at post-init and post-main states. Main
        // doesn't write 'a', so 'a' always equals the init write's value.
        // CopyPropagation correctly substitutes the property read with 1.
        // The resulting model may have no variables left - that's a valid
        // semantic outcome, not an optimizer bug.
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
}
