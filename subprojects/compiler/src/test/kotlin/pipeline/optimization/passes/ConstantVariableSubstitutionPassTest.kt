/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.optimization.passes

import hu.bme.mit.semantifyr.compiler.pipeline.optimization.analyses.ConstantValueAnalysis
import org.junit.jupiter.api.Test

class ConstantVariableSubstitutionPassTest : PassTestBase() {

    @Test
    fun `variable whose initializer and only assignment agree is substituted`() = assertPassTransforms(
        source = """
            inlined oxsts of semantifyr::Anything
            var a : int := 7
            init { a := 7 }
            tran { }
            prop { AG (a == 7) }
        """,
        expectedSource = """
            inlined oxsts of semantifyr::Anything
            var a : int := 7
            init { a := 7 }
            tran { }
            prop { AG (7 == 7) }
        """,
        analysisClasses = listOf(ConstantValueAnalysis::class.java),
    ) {
        it.getInstance(ConstantVariableSubstitutionPass::class.java)
    }

    @Test
    fun `havoced variable is not treated as constant`() = assertPassTransforms(
        source = """
            inlined oxsts of semantifyr::Anything
            var a : int := 0
            init { }
            tran {
                havoc(a)
                a := 7
            }
            prop { AG (a == 7) }
        """,
        expectedSource = """
            inlined oxsts of semantifyr::Anything
            var a : int := 0
            init { }
            tran {
                havoc(a)
                a := 7
            }
            prop { AG (a == 7) }
        """,
        analysisClasses = listOf(ConstantValueAnalysis::class.java),
    ) {
        it.getInstance(ConstantVariableSubstitutionPass::class.java)
    }

    @Test
    fun `variable with differing writes is not treated as constant`() = assertPassTransforms(
        source = """
            inlined oxsts of semantifyr::Anything
            var a : int := 0
            init { }
            tran {
                choice { a := 1 } or { a := 2 }
            }
            prop { AG (a != 3) }
        """,
        expectedSource = """
            inlined oxsts of semantifyr::Anything
            var a : int := 0
            init { }
            tran {
                choice { a := 1 } or { a := 2 }
            }
            prop { AG (a != 3) }
        """,
        analysisClasses = listOf(ConstantValueAnalysis::class.java),
    ) {
        it.getInstance(ConstantVariableSubstitutionPass::class.java)
    }
}
