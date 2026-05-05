/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.optimization.patterns

import org.junit.jupiter.api.Test

class RedundancyPatternsTest : PatternTestBase() {
    @Test
    fun `if with constant true guard is replaced by its body`() = assertPatternTransforms(
        pattern = ConstantGuardIfPattern(),
        input = """
            inlined oxsts of semantifyr::Anything
            var a : int := 0
            init { }
            tran {
                if (true) { a := 1 } else { a := 2 }
            }
            prop { AG true }
        """,
        expected = """
            inlined oxsts of semantifyr::Anything
            var a : int := 0
            init { }
            tran { { a := 1 } }
            prop { AG true }
        """,
    )

    @Test
    fun `if with constant false guard and no else is removed`() = assertPatternTransforms(
        pattern = ConstantGuardIfPattern(),
        input = """
            inlined oxsts of semantifyr::Anything
            var a : int := 0
            init { }
            tran {
                a := 0
                if (false) { a := 1 }
            }
            prop { AG true }
        """,
        expected = """
            inlined oxsts of semantifyr::Anything
            var a : int := 0
            init { }
            tran { a := 0 }
            prop { AG true }
        """,
    )

    @Test
    fun `assume of constant true is removed`() = assertPatternTransforms(
        pattern = RemoveConstantTrueAssumptionPattern(),
        input = """
            inlined oxsts of semantifyr::Anything
            var a : int := 0
            init { }
            tran {
                assume(true)
                a := 1
            }
            prop { AG true }
        """,
        expected = """
            inlined oxsts of semantifyr::Anything
            var a : int := 0
            init { }
            tran { a := 1 }
            prop { AG true }
        """,
    )

    @Test
    fun `empty if body with no else is removed`() = assertPatternTransforms(
        pattern = RemoveEmptyIfBodyPattern(),
        input = """
            inlined oxsts of semantifyr::Anything
            var a : int := 0
            init { }
            tran {
                a := 0
                if (a == 0) { }
            }
            prop { AG true }
        """,
        expected = """
            inlined oxsts of semantifyr::Anything
            var a : int := 0
            init { }
            tran { a := 0 }
            prop { AG true }
        """,
    )

    @Test
    fun `empty else is dropped`() = assertPatternTransforms(
        pattern = RemoveEmptyIfElsePattern(),
        input = """
            inlined oxsts of semantifyr::Anything
            var a : int := 0
            init { }
            tran { if (a == 0) { a := 1 } else { } }
            prop { AG true }
        """,
        expected = """
            inlined oxsts of semantifyr::Anything
            var a : int := 0
            init { }
            tran { if (a == 0) { a := 1 } }
            prop { AG true }
        """,
    )

    @Test
    fun `for loop with empty body is removed`() = assertPatternTransforms(
        pattern = RemoveEmptyForPattern(),
        input = """
            inlined oxsts of semantifyr::Anything
            var a : int := 0
            init { }
            tran {
                a := 1
                for (i in 0 .. 5) { }
            }
            prop { AG true }
        """,
        expected = """
            inlined oxsts of semantifyr::Anything
            var a : int := 0
            init { }
            tran { a := 1 }
            prop { AG true }
        """,
    )

    @Test
    fun `choice with two empty branches drops one`() = assertPatternTransforms(
        pattern = RemoveRedundantEmptyChoiceBranchPattern(),
        input = """
            inlined oxsts of semantifyr::Anything
            var a : int := 0
            init { }
            tran {
                choice { } or { } or { a := 1 }
            }
            prop { AG true }
        """,
        expected = """
            inlined oxsts of semantifyr::Anything
            var a : int := 0
            init { }
            tran {
                choice { } or { a := 1 }
            }
            prop { AG true }
        """,
    )
}
