/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.optimization.patterns.expression

import hu.bme.mit.semantifyr.compiler.pipeline.optimization.patterns.PatternTestBase
import org.junit.jupiter.api.Test

class TemporalOperatorPatternsTest : PatternTestBase() {
    @Test
    fun `not AG rewrites to EF not`() = assertPatternTransforms(
        pattern = BubbleNotAGPattern(),
        input = """
            inlined oxsts of semantifyr::Anything
            var a : bool := false
            init { }
            tran { }
            prop { !(AG a) }
        """,
        expected = """
            inlined oxsts of semantifyr::Anything
            var a : bool := false
            init { }
            tran { }
            prop { EF !a }
        """,
    )

    @Test
    fun `not EF rewrites to AG not`() = assertPatternTransforms(
        pattern = BubbleNotEFPattern(),
        input = """
            inlined oxsts of semantifyr::Anything
            var a : bool := false
            init { }
            tran { }
            prop { !(EF a) }
        """,
        expected = """
            inlined oxsts of semantifyr::Anything
            var a : bool := false
            init { }
            tran { }
            prop { AG !a }
        """,
    )
}
