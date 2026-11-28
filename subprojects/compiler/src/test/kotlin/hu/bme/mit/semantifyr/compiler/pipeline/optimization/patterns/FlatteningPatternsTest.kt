/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.optimization.patterns

import hu.bme.mit.semantifyr.oxsts.lang.tests.utils.TestModels.testModel
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
        input = testModel(javaClass, "nested_sequence.semantifyr"),
        expected = testModel(javaClass, "nested_sequence_expected.semantifyr"),
    )

    @Test
    fun `choice branch whose only step is a choice is hoisted into the outer choice`() = assertPatternTransforms(
        pattern = FlattenNestedChoicePattern(),
        input = testModel(javaClass, "nested_choice.semantifyr"),
        expected = testModel(javaClass, "nested_choice_expected.semantifyr"),
    )
}
