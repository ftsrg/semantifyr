/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.patterns

import hu.bme.mit.semantifyr.compiler.pipeline.optimization.patterns.PatternTestBase
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.patterns.PropagateBothBranchesConstantFalsePattern
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.patterns.PropagateConstantFalseInSequencePattern
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.patterns.PropagateSingleBranchConstantFalsePattern
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.patterns.RemoveConstantFalseChoiceBranchPattern
import org.junit.jupiter.api.Test

class AssumeFalsePropagationPatternsTest : PatternTestBase() {
    @Test
    fun `sequence with a constant-false assumption collapses to just the assumption`() = assertPatternTransforms(
        pattern = PropagateConstantFalseInSequencePattern(),
        input = """
            inlined oxsts of semantifyr::Anything
            var a : int := 0
            init { }
            tran {
                a := 1
                assume(false)
                a := 2
            }
            prop { AG true }
        """,
        expected = """
            inlined oxsts of semantifyr::Anything
            var a : int := 0
            init { }
            tran { assume(false) }
            prop { AG true }
        """,
    )

    @Test
    fun `if with both branches constant-false assumes is replaced by the body`() = assertPatternTransforms(
        pattern = PropagateBothBranchesConstantFalsePattern(),
        input = """
            inlined oxsts of semantifyr::Anything
            var a : int := 0
            init { }
            tran {
                if (a == 0) { assume(false) } else { assume(false) }
            }
            prop { AG true }
        """,
        expected = """
            inlined oxsts of semantifyr::Anything
            var a : int := 0
            init { }
            tran { { assume(false) } }
            prop { AG true }
        """,
    )

    @Test
    fun `single-branch choice with only constant-false assume collapses to assume`() = assertPatternTransforms(
        pattern = PropagateSingleBranchConstantFalsePattern(),
        input = """
            inlined oxsts of semantifyr::Anything
            var a : int := 0
            init { }
            tran {
                choice { assume(false) }
            }
            prop { AG true }
        """,
        expected = """
            inlined oxsts of semantifyr::Anything
            var a : int := 0
            init { }
            tran { assume(false) }
            prop { AG true }
        """,
    )

    @Test
    fun `constant-false branch in multi-branch choice is removed`() = assertPatternTransforms(
        pattern = RemoveConstantFalseChoiceBranchPattern(),
        input = """
            inlined oxsts of semantifyr::Anything
            var a : int := 0
            init { }
            tran {
                choice { assume(false) } or { a := 1 }
            }
            prop { AG true }
        """,
        expected = """
            inlined oxsts of semantifyr::Anything
            var a : int := 0
            init { }
            tran {
                choice { a := 1 }
            }
            prop { AG true }
        """,
    )
}
