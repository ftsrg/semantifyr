/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.optimization.patterns.expression

import hu.bme.mit.semantifyr.compiler.pipeline.optimization.patterns.PatternTestBase
import org.junit.jupiter.api.Test

class StructuralEqualityPatternsTest : PatternTestBase() {
    @Test
    fun `subtract of equal pure operands collapses to zero`() = assertPatternTransforms(
        pattern = SelfArithmeticPattern(),
        input = """
            inlined oxsts of semantifyr::Anything
            var a : int := 0
            init { }
            tran { }
            prop { AG (a - a == 0) }
        """,
        expected = """
            inlined oxsts of semantifyr::Anything
            var a : int := 0
            init { }
            tran { }
            prop { AG (0 == 0) }
        """,
    )

    @Test
    fun `equality of identical pure operands collapses to true`() = assertPatternTransforms(
        pattern = SelfComparisonPattern(),
        input = """
            inlined oxsts of semantifyr::Anything
            var a : int := 0
            init { }
            tran { }
            prop { AG (a == a) }
        """,
        expected = """
            inlined oxsts of semantifyr::Anything
            var a : int := 0
            init { }
            tran { }
            prop { AG true }
        """,
    )

    @Test
    fun `less-than of identical operands collapses to false`() = assertPatternTransforms(
        pattern = SelfComparisonPattern(),
        input = """
            inlined oxsts of semantifyr::Anything
            var a : int := 0
            init { }
            tran { }
            prop { AG (a < a) }
        """,
        expected = """
            inlined oxsts of semantifyr::Anything
            var a : int := 0
            init { }
            tran { }
            prop { AG false }
        """,
    )
}
