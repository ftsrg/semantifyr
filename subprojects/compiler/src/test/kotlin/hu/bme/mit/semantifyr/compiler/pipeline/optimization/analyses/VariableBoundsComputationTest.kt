/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.optimization.analyses

import hu.bme.mit.semantifyr.compiler.pipeline.utils.eAllOfType
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ArithmeticBinaryOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ArithmeticOp
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ForOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class VariableBoundsComputationTest : AnalysisTestBase() {

    @Test
    fun `a counter without a guard is unbounded above`() {
        val (inlined, result) = runVariableBounds(
            """
                inlined oxsts of semantifyr::Anything
                var x : int := 0
                init { }
                tran { x := x + 1 }
                prop { AG (x >= 0) }
            """,
        )

        assertThat(result.variableRanges[inlined.varNamed("x")]).isEqualTo(IntegerRange(0, null))
    }

    @Test
    fun `a saturating counter is bounded by its guard`() {
        val (inlined, result) = runVariableBounds(
            """
                inlined oxsts of semantifyr::Anything
                var x : int := 0
                init { }
                tran { if (x < 10) { x := x + 1 } }
                prop { AG (x <= 10) }
            """,
        )

        assertThat(result.variableRanges[inlined.varNamed("x")]).isEqualTo(IntegerRange(0, 10))
    }

    @Test
    fun `an assumption before an increment bounds it`() {
        val (inlined, result) = runVariableBounds(
            """
                inlined oxsts of semantifyr::Anything
                var x : int := 0
                init { }
                tran {
                    assume(x < 5)
                    x := x + 1
                }
                prop { AG (x <= 5) }
            """,
        )

        assertThat(result.variableRanges[inlined.varNamed("x")]).isEqualTo(IntegerRange(0, 5))
    }

    @Test
    fun `a guarded decrement with a reset is bounded on both sides`() {
        val (inlined, result) = runVariableBounds(
            """
                inlined oxsts of semantifyr::Anything
                var t : int := 0
                init { t := 3 }
                tran {
                    choice {
                        if (t > 0) { t := t - 1 }
                    } or {
                        t := 3
                    }
                }
                prop { AG (t >= 0) }
            """,
        )

        assertThat(result.variableRanges[inlined.varNamed("t")]).isEqualTo(IntegerRange(0, 3))
    }

    @Test
    fun `a negated guard bounds the else branch`() {
        val (inlined, result) = runVariableBounds(
            """
                inlined oxsts of semantifyr::Anything
                var x : int := 0
                init { }
                tran {
                    if (!(x >= 4)) { x := x + 2 } else { x := 0 }
                }
                prop { AG (x <= 5) }
            """,
        )

        assertThat(result.variableRanges[inlined.varNamed("x")]).isEqualTo(IntegerRange(0, 5))
    }

    @Test
    fun `a constant range loop is unrolled and bounds its variable`() {
        val (inlined, result) = runVariableBounds(
            """
                inlined oxsts of semantifyr::Anything
                var x : int := 0
                init { }
                tran {
                    assume(x == 0)
                    for (i in 0 .. 2) { x := x + i }
                }
                prop { AG (x <= 3) }
            """,
        )

        assertThat(result.variableRanges[inlined.loopVariable()]).isEqualTo(IntegerRange(0, 2))
        assertThat(result.variableRanges[inlined.varNamed("x")]).isEqualTo(IntegerRange(0, 3))
    }

    @Test
    fun `a loop over a variable range takes the range of its bounds`() {
        val (inlined, result) = runVariableBounds(
            """
                inlined oxsts of semantifyr::Anything
                var n : int := 0
                var x : int := 0
                init { }
                tran {
                    choice { n := 4 } or { n := 7 }
                    x := 0
                    for (i in 1 ..< n) { x := i }
                }
                prop { AG (x < 7) }
            """,
        )

        assertThat(result.variableRanges[inlined.loopVariable()]).isEqualTo(IntegerRange(1, 6))
        assertThat(result.variableRanges[inlined.varNamed("x")]).isEqualTo(IntegerRange(0, 6))
    }

    @Test
    fun `instance ids and nothing are bounded by the literals assigned`() {
        val (inlined, result) = runVariableBounds(
            """
                inlined oxsts of semantifyr::Anything
                var current : int := -1
                init { }
                tran {
                    choice { current := 0 } or { current := 2 } or { current := -1 }
                }
                prop { AG (current != 1) }
            """,
        )

        assertThat(result.variableRanges[inlined.varNamed("current")]).isEqualTo(IntegerRange(-1, 2))
    }

    @Test
    fun `boolean variables have no range`() {
        val (inlined, result) = runVariableBounds(
            """
                inlined oxsts of semantifyr::Anything
                var b : bool := false
                init { }
                tran { b := !b }
                prop { AG (b || !b) }
            """,
        )

        assertThat(result.variableRanges).doesNotContainKey(inlined.varNamed("b"))
    }

    @Test
    fun `a havoc is unbounded unless a havoc range is given`() {
        val source = """
            inlined oxsts of semantifyr::Anything
            var x : int := 0
            var y : int := 0
            init { }
            tran {
                havoc(x)
                assume(x >= 1 && x <= 3)
                y := x
            }
            prop { AG (y <= 3) }
        """

        val (inlined, unbounded) = runVariableBounds(source)
        assertThat(unbounded.variableRanges[inlined.varNamed("x")]).isEqualTo(IntegerRange.UNBOUNDED)
        assertThat(unbounded.variableRanges[inlined.varNamed("y")]).isEqualTo(IntegerRange(0, 3))

        val (candidates, bounded) = runVariableBounds(source) {
            IntegerRange(0, 4)
        }
        assertThat(bounded.variableRanges[candidates.varNamed("x")]).isEqualTo(IntegerRange(0, 4))
    }

    @Test
    fun `a limit bounds every value but not the arithmetic that leaves it`() {
        val (inlined, result) = runVariableBounds(
            """
                inlined oxsts of semantifyr::Anything
                var x : int := 0
                var y : int := 0
                init { }
                tran {
                    x := x + 1
                    y := x / 2
                }
                prop { AG (y >= 0) }
            """,
            limit = {
                IntegerRange(-100, 100)
            },
        )

        val increment = inlined.eAllOfType<ArithmeticBinaryOperator>().single {
            it.op == ArithmeticOp.ADD
        }
        assertThat(result.variableRanges[inlined.varNamed("x")]).isEqualTo(IntegerRange(0, 100))
        assertThat(result.variableRanges[inlined.varNamed("y")]).isEqualTo(IntegerRange(0, 50))
        assertThat(result.arithmeticRanges[increment]).isEqualTo(IntegerRange(1, 101))
    }

    @Test
    fun `an uninitialized variable is unbounded before init writes it`() {
        val (inlined, result) = runVariableBounds(
            """
                inlined oxsts of semantifyr::Anything
                var x : int
                init { x := 0 }
                tran { if (x < 2) { x := x + 1 } }
                prop { AG (x <= 2) }
            """,
        )

        assertThat(result.variableRanges[inlined.varNamed("x")]).isEqualTo(IntegerRange.UNBOUNDED)
    }

    @Test
    fun `a local variable is bounded by its initializer and writes`() {
        val (inlined, result) = runVariableBounds(
            """
                inlined oxsts of semantifyr::Anything
                var x : int := 0
                init { }
                tran {
                    var step : int := 2
                    if (x < 6) { x := x + step }
                }
                prop { AG (x <= 7) }
            """,
        )

        assertThat(result.variableRanges[inlined.varNamed("step")]).isEqualTo(IntegerRange(2, 2))
        assertThat(result.variableRanges[inlined.varNamed("x")]).isEqualTo(IntegerRange(0, 7))
    }

    @Test
    fun `an intermediate product can exceed the range of the variable it is assigned to`() {
        val (inlined, result) = runVariableBounds(
            """
                inlined oxsts of semantifyr::Anything
                var x : int := 0
                var y : int := 0
                init { }
                tran {
                    if (x < 5000) { x := x + 1 }
                    y := (x * 1000000) / 1000000
                }
                prop { AG (y <= 5000) }
            """,
        )

        val product = inlined.eAllOfType<ArithmeticBinaryOperator>().single {
            it.op == ArithmeticOp.MUL
        }
        assertThat(result.variableRanges[inlined.varNamed("y")]).isEqualTo(IntegerRange(0, 5000))
        assertThat(result.arithmeticRanges[product]).isEqualTo(IntegerRange(1_000_000, 5_000_000_000))
    }

    @Test
    fun `division by a range containing zero is unbounded`() {
        val (inlined, result) = runVariableBounds(
            """
                inlined oxsts of semantifyr::Anything
                var d : int := 0
                var q : int := 0
                init { }
                tran {
                    choice { d := 0 } or { d := 2 }
                    q := 10 / d
                }
                prop { AG (q <= 10) }
            """,
        )

        assertThat(result.variableRanges[inlined.varNamed("q")]).isEqualTo(IntegerRange.UNBOUNDED)
    }

    @Test
    fun `code no run reaches records nothing`() {
        val (inlined, result) = runVariableBounds(
            """
                inlined oxsts of semantifyr::Anything
                var x : int := 0
                init { }
                tran {
                    assume(x > 0)
                    x := x * 2
                }
                prop { AG (x == 0) }
            """,
        )

        assertThat(result.variableRanges[inlined.varNamed("x")]).isEqualTo(IntegerRange(0, 0))
        assertThat(result.arithmeticRanges).isEmpty()
    }

    @Test
    fun `a variable copied from a bounded one is bounded`() {
        val (inlined, result) = runVariableBounds(
            """
                inlined oxsts of semantifyr::Anything
                var x : int := 0
                var y : int := 0
                init { }
                tran {
                    if (x < 10) { x := x + 1 } else { x := 0 }
                    y := x * 2
                }
                prop { AG (y <= 20) }
            """,
        )

        assertThat(result.variableRanges[inlined.varNamed("y")]).isEqualTo(IntegerRange(0, 20))
    }

    private fun InlinedOxsts.varNamed(name: String): VariableDeclaration {
        return eAllOfType<VariableDeclaration>().single {
            it.name == name
        }
    }

    private fun InlinedOxsts.loopVariable(): VariableDeclaration {
        return eAllOfType<ForOperation>().single().loopVariable
    }

}
