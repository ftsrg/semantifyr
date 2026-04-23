/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.optimization.patterns.expression

import com.google.inject.Inject
import hu.bme.mit.semantifyr.compiler.pipeline.expression.ConstantExpressionEvaluationTransformer
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.patterns.PatternTestBase
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.ConstantExpressionEvaluatorProvider
import org.junit.jupiter.api.Test

class ConstantFoldingPatternTest : PatternTestBase() {

    @Inject
    private lateinit var evaluatorProvider: ConstantExpressionEvaluatorProvider

    private fun pattern(): ConstantFoldingPattern {
        return ConstantFoldingPattern(evaluatorProvider, ConstantExpressionEvaluationTransformer())
    }

    @Test
    fun `pure operator expression over literals folds to a literal`() = assertPatternTransforms(
        pattern = pattern(),
        input = """
            inlined oxsts of semantifyr::Anything
            init { }
            tran { }
            prop { AG (1 + 2 == 3) }
        """,
        expected = """
            inlined oxsts of semantifyr::Anything
            init { }
            tran { }
            prop { AG true }
        """,
    )

    @Test
    fun `expression with non-constant operand is not folded`() = assertPatternDoesNotMatch(
        pattern = pattern(),
        input = """
            inlined oxsts of semantifyr::Anything
            var a : int := 0
            init { }
            tran { }
            prop { AG (a + 1 == 0) }
        """,
    )
}
