/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.optimization.patterns

import hu.bme.mit.semantifyr.compiler.pipeline.optimization.patterns.PatternTestBase
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.patterns.expression.DeMorganPattern
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.patterns.expression.DoubleNegationPattern
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.patterns.expression.NegatedComparisonPattern
import org.junit.jupiter.api.Test

class NegationPropagationPatternsTest : PatternTestBase() {

    @Test
    fun `double negation cancels`() = assertPatternTransforms(
        pattern = DoubleNegationPattern(),
        input = """
            inlined oxsts of semantifyr::Anything
            var a : bool := false
            init { }
            tran { }
            prop { AG !(!a) }
        """,
        expected = """
            inlined oxsts of semantifyr::Anything
            var a : bool := false
            init { }
            tran { }
            prop { AG a }
        """,
    )

    @Test
    fun `negated equality flips to not-equal`() = assertPatternTransforms(
        pattern = NegatedComparisonPattern(),
        input = """
            inlined oxsts of semantifyr::Anything
            var a : int := 0
            init { }
            tran { }
            prop { AG !(a == 0) }
        """,
        expected = """
            inlined oxsts of semantifyr::Anything
            var a : int := 0
            init { }
            tran { }
            prop { AG (a != 0) }
        """,
    )

    @Test
    fun `negated less flips to greater-or-equal`() = assertPatternTransforms(
        pattern = NegatedComparisonPattern(),
        input = """
            inlined oxsts of semantifyr::Anything
            var a : int := 0
            init { }
            tran { }
            prop { AG !(a < 0) }
        """,
        expected = """
            inlined oxsts of semantifyr::Anything
            var a : int := 0
            init { }
            tran { }
            prop { AG (a >= 0) }
        """,
    )

    @Test
    fun `de Morgan over and rewrites to or of negations`() = assertPatternTransforms(
        pattern = DeMorganPattern(),
        input = """
            inlined oxsts of semantifyr::Anything
            var a : bool := false
            var b : bool := false
            init { }
            tran { }
            prop { AG !(a && b) }
        """,
        expected = """
            inlined oxsts of semantifyr::Anything
            var a : bool := false
            var b : bool := false
            init { }
            tran { }
            prop { AG (!a || !b) }
        """,
    )

    @Test
    fun `de Morgan over or rewrites to and of negations`() = assertPatternTransforms(
        pattern = DeMorganPattern(),
        input = """
            inlined oxsts of semantifyr::Anything
            var a : bool := false
            var b : bool := false
            init { }
            tran { }
            prop { AG !(a || b) }
        """,
        expected = """
            inlined oxsts of semantifyr::Anything
            var a : bool := false
            var b : bool := false
            init { }
            tran { }
            prop { AG (!a && !b) }
        """,
    )
}
