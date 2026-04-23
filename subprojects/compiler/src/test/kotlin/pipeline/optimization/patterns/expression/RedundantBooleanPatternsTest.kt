/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.optimization.patterns

import hu.bme.mit.semantifyr.compiler.pipeline.optimization.patterns.expression.ConstantFalseAndPattern
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.patterns.expression.ConstantTrueOrPattern
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.patterns.expression.IdempotentBooleanPattern
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.patterns.expression.RedundantAndPattern
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.patterns.expression.RedundantOrPattern
import org.junit.jupiter.api.Test

class RedundantBooleanPatternsTest : PatternTestBase() {

    @Test
    fun `and with literal true on the left collapses to right`() = assertPatternTransforms(
        pattern = RedundantAndPattern(),
        input = """
            inlined oxsts of semantifyr::Anything
            var a : bool := false
            init { }
            tran { }
            prop { AG (true && a) }
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
    fun `and with literal true on the right collapses to left`() = assertPatternTransforms(
        pattern = RedundantAndPattern(),
        input = """
            inlined oxsts of semantifyr::Anything
            var a : bool := false
            init { }
            tran { }
            prop { AG (a && true) }
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
    fun `or with literal false on the left collapses to right`() = assertPatternTransforms(
        pattern = RedundantOrPattern(),
        input = """
            inlined oxsts of semantifyr::Anything
            var a : bool := false
            init { }
            tran { }
            prop { AG (false || a) }
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
    fun `and with literal false on the left collapses to false`() = assertPatternTransforms(
        pattern = ConstantFalseAndPattern(),
        input = """
            inlined oxsts of semantifyr::Anything
            var a : bool := false
            init { }
            tran { }
            prop { AG (false && a) }
        """,
        expected = """
            inlined oxsts of semantifyr::Anything
            var a : bool := false
            init { }
            tran { }
            prop { AG false }
        """,
    )

    @Test
    fun `or with literal true on the left collapses to true`() = assertPatternTransforms(
        pattern = ConstantTrueOrPattern(),
        input = """
            inlined oxsts of semantifyr::Anything
            var a : bool := false
            init { }
            tran { }
            prop { AG (true || a) }
        """,
        expected = """
            inlined oxsts of semantifyr::Anything
            var a : bool := false
            init { }
            tran { }
            prop { AG true }
        """,
    )

    @Test
    fun `idempotent and of structurally equal pure operands collapses`() = assertPatternTransforms(
        pattern = IdempotentBooleanPattern(),
        input = """
            inlined oxsts of semantifyr::Anything
            var a : bool := false
            init { }
            tran { }
            prop { AG (a && a) }
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
    fun `and without constant operand is left untouched`() = assertPatternDoesNotMatch(
        pattern = RedundantAndPattern(),
        input = """
            inlined oxsts of semantifyr::Anything
            var a : bool := false
            var b : bool := false
            init { }
            tran { }
            prop { AG (a && b) }
        """,
    )
}
