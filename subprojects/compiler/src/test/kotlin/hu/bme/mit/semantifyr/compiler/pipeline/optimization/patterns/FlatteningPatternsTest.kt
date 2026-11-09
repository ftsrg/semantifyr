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
            var first: int := 0
            init {
            }
            tran {
                choice {
                    first := 1
                }
            }
            prop {
                AG true
            }
        """,
        expected = """
            inlined oxsts of semantifyr::Anything
            var first: int := 0
            init {
            }
            tran {
                {
                    first := 1
                }
            }
            prop {
                AG true
            }
        """,
    )

    @Test
    fun `nested sequence inside a sequence is inlined`() = assertPatternTransforms(
        pattern = FlattenNestedSequencePattern(),
        input = """
            inlined oxsts of semantifyr::Anything
            var first: int := 0
            var second: int := 0
            init {
            }
            tran {
                first := 1
                {
                    second := 2
                    first := 3
                }
                second := 4
            }
            prop {
                AG true
            }
        """,
        expected = """
            inlined oxsts of semantifyr::Anything
            var first: int := 0
            var second: int := 0
            init {
            }
            tran {
                first := 1
                second := 2
                first := 3
                second := 4
            }
            prop {
                AG true
            }
        """,
    )

    @Test
    fun `choice branch whose only step is a choice is hoisted into the outer choice`() = assertPatternTransforms(
        pattern = FlattenNestedChoicePattern(),
        input = """
            inlined oxsts of semantifyr::Anything
            var first: int := 0
            init {
            }
            tran {
                choice {
                    choice {
                        first := 1
                    } or {
                        first := 2
                    }
                } or {
                    first := 3
                }
            }
            prop {
                AG true
            }
        """,
        expected = """
            inlined oxsts of semantifyr::Anything
            var first: int := 0
            init {
            }
            tran {
                choice {
                    first := 3
                } or {
                    first := 1
                } or {
                    first := 2
                }
            }
            prop {
                AG true
            }
        """,
    )
}
