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
            var value : int := 0
            init { }
            tran {
                if (true) { value := 1 } else { value := 2 }
            }
            prop { AG true }
        """,
        expected = """
            inlined oxsts of semantifyr::Anything
            var value : int := 0
            init { }
            tran { { value := 1 } }
            prop { AG true }
        """,
    )

    @Test
    fun `if with constant false guard and no else is removed`() = assertPatternTransforms(
        pattern = ConstantGuardIfPattern(),
        input = """
            inlined oxsts of semantifyr::Anything
            var value : int := 0
            init { }
            tran {
                value := 0
                if (false) { value := 1 }
            }
            prop { AG true }
        """,
        expected = """
            inlined oxsts of semantifyr::Anything
            var value : int := 0
            init { }
            tran { value := 0 }
            prop { AG true }
        """,
    )

    @Test
    fun `assume of constant true is removed`() = assertPatternTransforms(
        pattern = RemoveConstantTrueAssumptionPattern(),
        input = """
            inlined oxsts of semantifyr::Anything
            var value : int := 0
            init { }
            tran {
                assume(true)
                value := 1
            }
            prop { AG true }
        """,
        expected = """
            inlined oxsts of semantifyr::Anything
            var value : int := 0
            init { }
            tran { value := 1 }
            prop { AG true }
        """,
    )

    @Test
    fun `empty if body with no else is removed`() = assertPatternTransforms(
        pattern = RemoveEmptyIfBodyPattern(),
        input = """
            inlined oxsts of semantifyr::Anything
            var value : int := 0
            init { }
            tran {
                value := 0
                if (value == 0) { }
            }
            prop { AG true }
        """,
        expected = """
            inlined oxsts of semantifyr::Anything
            var value : int := 0
            init { }
            tran { value := 0 }
            prop { AG true }
        """,
    )

    @Test
    fun `empty else is dropped`() = assertPatternTransforms(
        pattern = RemoveEmptyIfElsePattern(),
        input = """
            inlined oxsts of semantifyr::Anything
            var value : int := 0
            init { }
            tran { if (value == 0) { value := 1 } else { } }
            prop { AG true }
        """,
        expected = """
            inlined oxsts of semantifyr::Anything
            var value : int := 0
            init { }
            tran { if (value == 0) { value := 1 } }
            prop { AG true }
        """,
    )

    @Test
    fun `for loop with empty body is removed`() = assertPatternTransforms(
        pattern = RemoveEmptyForPattern(),
        input = """
            inlined oxsts of semantifyr::Anything
            var value : int := 0
            init { }
            tran {
                value := 1
                for (index in 0..5) { }
            }
            prop { AG true }
        """,
        expected = """
            inlined oxsts of semantifyr::Anything
            var value : int := 0
            init { }
            tran { value := 1 }
            prop { AG true }
        """,
    )

    @Test
    fun `choice with two empty branches drops one`() = assertPatternTransforms(
        pattern = RemoveRedundantEmptyChoiceBranchPattern(),
        input = """
            inlined oxsts of semantifyr::Anything
            var value : int := 0
            init { }
            tran {
                choice { } or { } or { value := 1 }
            }
            prop { AG true }
        """,
        expected = """
            inlined oxsts of semantifyr::Anything
            var value : int := 0
            init { }
            tran {
                choice { } or { value := 1 }
            }
            prop { AG true }
        """,
    )
}
