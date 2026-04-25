/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.optimization.passes

import org.junit.jupiter.api.Test

class VariableLivenessPassTest : PassTestBase() {
    @Test
    fun `unread variable and its assignments are removed`() = assertPassTransforms(
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
            init { }
            tran { a := 1 }
            prop { AG (a == 1) }
        """,
    ) {
        it.getInstance(VariableLivenessPass::class.java)
    }

    @Test
    fun `initializer-only variable is substituted into its reads`() = assertPassTransforms(
        source = """
            inlined oxsts of semantifyr::Anything
            var a : int := 7
            init { }
            tran { }
            prop { AG (a == 7) }
        """,
        expectedSource = """
            inlined oxsts of semantifyr::Anything
            init { }
            tran { }
            prop { AG (7 == 7) }
        """,
    ) {
        it.getInstance(VariableLivenessPass::class.java)
    }

    @Test
    fun `single unused variable is fully eliminated, yielding a zero-variable model`() = assertPassTransforms(
        source = """
            inlined oxsts of semantifyr::Anything
            var dummy : int := 0
            init { }
            tran { }
            prop { AG true }
        """,
        expectedSource = """
            inlined oxsts of semantifyr::Anything
            init { }
            tran { }
            prop { AG true }
        """,
    ) {
        it.getInstance(VariableLivenessPass::class.java)
    }

    @Test
    fun `variable with both havoc and assignment is left alone when read`() = assertPassTransforms(
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
    ) {
        it.getInstance(VariableLivenessPass::class.java)
    }
}
