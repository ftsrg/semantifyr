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
}
