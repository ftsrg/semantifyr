/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.optimization.patterns

import hu.bme.mit.semantifyr.compiler.pipeline.optimization.patterns.PatternTestBase
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.patterns.expression.ArithmeticIdentityPattern
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.patterns.expression.DoubleUnaryMinusPattern
import org.junit.jupiter.api.Test

class AlgebraicPatternsTest : PatternTestBase() {

    @Test
    fun `add zero on left collapses to right`() = assertPatternTransforms(
        pattern = ArithmeticIdentityPattern(),
        input = """
            inlined oxsts of semantifyr::Anything
            var a : int := 0
            init { }
            tran { }
            prop { AG (0 + a == 0) }
        """,
        expected = """
            inlined oxsts of semantifyr::Anything
            var a : int := 0
            init { }
            tran { }
            prop { AG (a == 0) }
        """,
    )

    @Test
    fun `multiply by zero collapses to zero`() = assertPatternTransforms(
        pattern = ArithmeticIdentityPattern(),
        input = """
            inlined oxsts of semantifyr::Anything
            var a : int := 0
            init { }
            tran { }
            prop { AG (a * 0 == 0) }
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
    fun `multiply by one on left collapses to right`() = assertPatternTransforms(
        pattern = ArithmeticIdentityPattern(),
        input = """
            inlined oxsts of semantifyr::Anything
            var a : int := 0
            init { }
            tran { }
            prop { AG (1 * a == 0) }
        """,
        expected = """
            inlined oxsts of semantifyr::Anything
            var a : int := 0
            init { }
            tran { }
            prop { AG (a == 0) }
        """,
    )

    @Test
    fun `divide by one collapses to left`() = assertPatternTransforms(
        pattern = ArithmeticIdentityPattern(),
        input = """
            inlined oxsts of semantifyr::Anything
            var a : int := 0
            init { }
            tran { }
            prop { AG (a / 1 == 0) }
        """,
        expected = """
            inlined oxsts of semantifyr::Anything
            var a : int := 0
            init { }
            tran { }
            prop { AG (a == 0) }
        """,
    )

    @Test
    fun `double unary minus cancels`() = assertPatternTransforms(
        pattern = DoubleUnaryMinusPattern(),
        input = """
            inlined oxsts of semantifyr::Anything
            var a : int := 0
            init { }
            tran { }
            prop { AG (-(-a) == 0) }
        """,
        expected = """
            inlined oxsts of semantifyr::Anything
            var a : int := 0
            init { }
            tran { }
            prop { AG (a == 0) }
        """,
    )
}
