/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.optimization.patterns

import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationPattern
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.patterns.expression.ArithmeticIdentityPattern
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.patterns.expression.ConstantFalseAndPattern
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.patterns.expression.ConstantTrueOrPattern
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.patterns.expression.IfThenElseConstantGuardPattern
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.patterns.expression.IfThenElseIdenticalBranchesPattern
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.patterns.expression.SelfArithmeticPattern
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.patterns.expression.SelfComparisonPattern
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

class ValuelessOperandPatternsTest : PatternTestBase() {

    companion object {
        @JvmStatic
        fun `A Fold Keeps An Operand That May Have No Value`(): List<Arguments> {
            return listOf(
                Arguments.of(ArithmeticIdentityPattern(), "result := (1 / divisor) * 0"),
                Arguments.of(ArithmeticIdentityPattern(), "result := 0 * (1 / divisor)"),
                Arguments.of(SelfArithmeticPattern(), "result := 1 / divisor - 1 / divisor"),
                Arguments.of(SelfComparisonPattern(), "assume(1 / divisor == 1 / divisor)"),
                Arguments.of(ConstantFalseAndPattern(), "assume(false && 1 / divisor > 0)"),
                Arguments.of(ConstantTrueOrPattern(), "assume(1 / divisor > 0 || true)"),
                Arguments.of(IfThenElseConstantGuardPattern(), "result := if true then 0 else 1 / divisor"),
                Arguments.of(IfThenElseIdenticalBranchesPattern(), "result := if 1 / divisor > 0 then 0 else 0"),
                Arguments.of(RemoveEmptyIfBodyPattern(), "if (1 / divisor > 0) {\n}"),
                Arguments.of(RemoveEmptyForPattern(), "for (index in 0..1 / divisor) {\n}"),
                Arguments.of(ArithmeticIdentityPattern(), "result := slots[divisor] * 0"),
                Arguments.of(SelfComparisonPattern(), "assume(slots[divisor] == slots[divisor])"),
                Arguments.of(IfThenElseConstantGuardPattern(), "result := if false then slots[divisor] else 0"),
            )
        }
    }

    @ParameterizedTest
    @MethodSource
    fun `A Fold Keeps An Operand That May Have No Value`(pattern: OptimizationPattern, operation: String) {
        assertPatternDoesNotMatch(pattern, model(operation))
    }

    @Test
    fun `a division by a nonzero literal has a value and folds`() = assertPatternTransforms(
        pattern = ArithmeticIdentityPattern(),
        input = model("result := (divisor / 2) * 0"),
        expected = model("result := 0"),
    )

    private fun model(operation: String): String {
        return """
            inlined oxsts of semantifyr::Anything
            var divisor: int := 0
            var result: int := 0
            var slots: int[2] := [0, 0]
            init {
            }
            tran {
                $operation
            }
            prop {
                AG true
            }
        """
    }
}
