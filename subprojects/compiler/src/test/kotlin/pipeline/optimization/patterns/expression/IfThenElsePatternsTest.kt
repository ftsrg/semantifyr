/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.optimization.patterns

import hu.bme.mit.semantifyr.compiler.pipeline.optimization.patterns.expression.IfThenElseConstantGuardPattern
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.patterns.expression.IfThenElseIdenticalBranchesPattern
import org.junit.jupiter.api.Test

class IfThenElsePatternsTest : PatternTestBase() {
    @Test
    fun `if true then a else b collapses to a`() = assertPatternTransforms(
        pattern = IfThenElseConstantGuardPattern(),
        input = """
            inlined oxsts of semantifyr::Anything
            var a : int := 0
            init { }
            tran { }
            prop { AG (if true then a else 0) == 0 }
        """,
        expected = """
            inlined oxsts of semantifyr::Anything
            var a : int := 0
            init { }
            tran { }
            prop { AG a == 0 }
        """,
    )

    @Test
    fun `if false then a else b collapses to b`() = assertPatternTransforms(
        pattern = IfThenElseConstantGuardPattern(),
        input = """
            inlined oxsts of semantifyr::Anything
            var a : int := 0
            init { }
            tran { }
            prop { AG (if false then a else 0) == 0 }
        """,
        expected = """
            inlined oxsts of semantifyr::Anything
            var a : int := 0
            init { }
            tran { }
            prop { AG 0 == 0 }
        """,
    )

    @Test
    fun `if with identical branches collapses to the branch`() = assertPatternTransforms(
        pattern = IfThenElseIdenticalBranchesPattern(),
        input = """
            inlined oxsts of semantifyr::Anything
            var a : int := 0
            init { }
            tran { }
            prop { AG (if a == 0 then 5 else 5) == 5 }
        """,
        expected = """
            inlined oxsts of semantifyr::Anything
            var a : int := 0
            init { }
            tran { }
            prop { AG 5 == 5 }
        """,
    )

    @Test
    fun `if with non-literal guard is left alone`() = assertPatternDoesNotMatch(
        pattern = IfThenElseConstantGuardPattern(),
        input = """
            inlined oxsts of semantifyr::Anything
            var a : int := 0
            init { }
            tran { }
            prop { AG (if a == 0 then 1 else 2) >= 0 }
        """,
    )

    @Test
    fun `if with differing branches is left alone`() = assertPatternDoesNotMatch(
        pattern = IfThenElseIdenticalBranchesPattern(),
        input = """
            inlined oxsts of semantifyr::Anything
            var a : int := 0
            init { }
            tran { }
            prop { AG (if a == 0 then 1 else 2) >= 0 }
        """,
    )
}
