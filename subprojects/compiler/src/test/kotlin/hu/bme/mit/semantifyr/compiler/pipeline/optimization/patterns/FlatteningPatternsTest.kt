/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.optimization.patterns

import org.junit.jupiter.api.Test

class FlatteningPatternsTest : PatternTestBase() {
    @Test
    fun `single-branch choice collapses to its branch`() = assertPatternTransforms(
        pattern = FlattenSingleBranchChoicePattern(),
        input = """
            inlined oxsts of semantifyr::Anything
            var a : int := 0
            init { }
            tran {
                choice { a := 1 }
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
    fun `nested sequence inside a sequence is inlined`() = assertPatternTransforms(
        pattern = FlattenNestedSequencePattern(),
        input = """
            inlined oxsts of semantifyr::Anything
            var a : int := 0
            var b : int := 0
            init { }
            tran {
                a := 1
                {
                    b := 2
                    a := 3
                }
                b := 4
            }
            prop { AG true }
        """,
        expected = """
            inlined oxsts of semantifyr::Anything
            var a : int := 0
            var b : int := 0
            init { }
            tran {
                a := 1
                b := 2
                a := 3
                b := 4
            }
            prop { AG true }
        """,
    )

    @Test
    fun `choice branch whose only step is a choice is hoisted into the outer choice`() = assertPatternTransforms(
        pattern = FlattenNestedChoicePattern(),
        input = """
            inlined oxsts of semantifyr::Anything
            var a : int := 0
            init { }
            tran {
                choice {
                    choice { a := 1 } or { a := 2 }
                } or { a := 3 }
            }
            prop { AG true }
        """,
        expected = """
            inlined oxsts of semantifyr::Anything
            var a : int := 0
            init { }
            tran {
                choice { a := 3 } or { a := 1 } or { a := 2 }
            }
            prop { AG true }
        """,
    )
}
