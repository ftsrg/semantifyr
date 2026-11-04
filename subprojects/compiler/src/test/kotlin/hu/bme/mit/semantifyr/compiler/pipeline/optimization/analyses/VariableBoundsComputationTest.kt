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
                var counter : int := 0
                init { }
                tran { counter := counter + 1 }
                prop { AG (counter >= 0) }
            """,
        )

        assertThat(result.variableRanges[inlined.varNamed("counter")]).isEqualTo(IntegerRange(0, null))
    }

    @Test
    fun `a saturating counter is bounded by its guard`() {
        val (inlined, result) = runVariableBounds(
            """
                inlined oxsts of semantifyr::Anything
                var counter : int := 0
                init { }
                tran { if (counter < 10) { counter := counter + 1 } }
                prop { AG (counter <= 10) }
            """,
        )

        assertThat(result.variableRanges[inlined.varNamed("counter")]).isEqualTo(IntegerRange(0, 10))
    }

    @Test
    fun `an assumption before an increment bounds it`() {
        val (inlined, result) = runVariableBounds(
            """
                inlined oxsts of semantifyr::Anything
                var counter : int := 0
                init { }
                tran {
                    assume(counter < 5)
                    counter := counter + 1
                }
                prop { AG (counter <= 5) }
            """,
        )

        assertThat(result.variableRanges[inlined.varNamed("counter")]).isEqualTo(IntegerRange(0, 5))
    }

    @Test
    fun `a guarded decrement with a reset is bounded on both sides`() {
        val (inlined, result) = runVariableBounds(
            """
                inlined oxsts of semantifyr::Anything
                var timer : int := 0
                init { timer := 3 }
                tran {
                    choice {
                        if (timer > 0) { timer := timer - 1 }
                    } or {
                        timer := 3
                    }
                }
                prop { AG (timer >= 0) }
            """,
        )

        assertThat(result.variableRanges[inlined.varNamed("timer")]).isEqualTo(IntegerRange(0, 3))
    }

    @Test
    fun `a negated guard bounds the else branch`() {
        val (inlined, result) = runVariableBounds(
            """
                inlined oxsts of semantifyr::Anything
                var counter : int := 0
                init { }
                tran {
                    if (!(counter >= 4)) { counter := counter + 2 } else { counter := 0 }
                }
                prop { AG (counter <= 5) }
            """,
        )

        assertThat(result.variableRanges[inlined.varNamed("counter")]).isEqualTo(IntegerRange(0, 5))
    }

    @Test
    fun `a constant range loop is unrolled and bounds its variable`() {
        val (inlined, result) = runVariableBounds(
            """
                inlined oxsts of semantifyr::Anything
                var counter : int := 0
                init { }
                tran {
                    assume(counter == 0)
                    for (index in 0 .. 2) { counter := counter + index }
                }
                prop { AG (counter <= 3) }
            """,
        )

        assertThat(result.variableRanges[inlined.loopVariable()]).isEqualTo(IntegerRange(0, 2))
        assertThat(result.variableRanges[inlined.varNamed("counter")]).isEqualTo(IntegerRange(0, 3))
    }

    @Test
    fun `a loop over a variable range takes the range of its bounds`() {
        val (inlined, result) = runVariableBounds(
            """
                inlined oxsts of semantifyr::Anything
                var bound : int := 0
                var last : int := 0
                init { }
                tran {
                    choice { bound := 4 } or { bound := 7 }
                    last := 0
                    for (index in 1 ..< bound) { last := index }
                }
                prop { AG (last < 7) }
            """,
        )

        assertThat(result.variableRanges[inlined.loopVariable()]).isEqualTo(IntegerRange(1, 6))
        assertThat(result.variableRanges[inlined.varNamed("last")]).isEqualTo(IntegerRange(0, 6))
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
                var flag : bool := false
                init { }
                tran { flag := !flag }
                prop { AG (flag || !flag) }
            """,
        )

        assertThat(result.variableRanges).doesNotContainKey(inlined.varNamed("flag"))
    }

    @Test
    fun `a havoc is unbounded unless a havoc range is given`() {
        val source = """
            inlined oxsts of semantifyr::Anything
            var chosen : int := 0
            var copy : int := 0
            init { }
            tran {
                havoc(chosen)
                assume(chosen >= 1 && chosen <= 3)
                copy := chosen
            }
            prop { AG (copy <= 3) }
        """

        val (inlined, unbounded) = runVariableBounds(source)
        assertThat(unbounded.variableRanges[inlined.varNamed("chosen")]).isEqualTo(IntegerRange.UNBOUNDED)
        assertThat(unbounded.variableRanges[inlined.varNamed("copy")]).isEqualTo(IntegerRange(0, 3))

        val (candidates, bounded) = runVariableBounds(source) {
            IntegerRange(0, 4)
        }
        assertThat(bounded.variableRanges[candidates.varNamed("chosen")]).isEqualTo(IntegerRange(0, 4))
    }

    @Test
    fun `a limit bounds every value but not the arithmetic that leaves it`() {
        val (inlined, result) = runVariableBounds(
            """
                inlined oxsts of semantifyr::Anything
                var counter : int := 0
                var half : int := 0
                init { }
                tran {
                    counter := counter + 1
                    half := counter / 2
                }
                prop { AG (half >= 0) }
            """,
            limit = {
                IntegerRange(-100, 100)
            },
        )

        val increment = inlined.eAllOfType<ArithmeticBinaryOperator>().single {
            it.op == ArithmeticOp.ADD
        }
        assertThat(result.variableRanges[inlined.varNamed("counter")]).isEqualTo(IntegerRange(0, 100))
        assertThat(result.variableRanges[inlined.varNamed("half")]).isEqualTo(IntegerRange(0, 50))
        assertThat(result.arithmeticRanges[increment]).isEqualTo(IntegerRange(1, 101))
    }

    @Test
    fun `an uninitialized variable is unbounded before init writes it`() {
        val (inlined, result) = runVariableBounds(
            """
                inlined oxsts of semantifyr::Anything
                var counter : int
                init { counter := 0 }
                tran { if (counter < 2) { counter := counter + 1 } }
                prop { AG (counter <= 2) }
            """,
        )

        assertThat(result.variableRanges[inlined.varNamed("counter")]).isEqualTo(IntegerRange.UNBOUNDED)
    }

    @Test
    fun `a local variable is bounded by its initializer and writes`() {
        val (inlined, result) = runVariableBounds(
            """
                inlined oxsts of semantifyr::Anything
                var counter : int := 0
                init { }
                tran {
                    var step : int := 2
                    if (counter < 6) { counter := counter + step }
                }
                prop { AG (counter <= 7) }
            """,
        )

        assertThat(result.variableRanges[inlined.varNamed("step")]).isEqualTo(IntegerRange(2, 2))
        assertThat(result.variableRanges[inlined.varNamed("counter")]).isEqualTo(IntegerRange(0, 7))
    }

    @Test
    fun `an intermediate product can exceed the range of the variable it is assigned to`() {
        val (inlined, result) = runVariableBounds(
            """
                inlined oxsts of semantifyr::Anything
                var counter : int := 0
                var scaled : int := 0
                init { }
                tran {
                    if (counter < 5000) { counter := counter + 1 }
                    scaled := (counter * 1000000) / 1000000
                }
                prop { AG (scaled <= 5000) }
            """,
        )

        val product = inlined.eAllOfType<ArithmeticBinaryOperator>().single {
            it.op == ArithmeticOp.MUL
        }
        assertThat(result.variableRanges[inlined.varNamed("scaled")]).isEqualTo(IntegerRange(0, 5000))
        assertThat(result.arithmeticRanges[product]).isEqualTo(IntegerRange(1_000_000, 5_000_000_000))
    }

    @Test
    fun `division by a range containing zero is unbounded`() {
        val (inlined, result) = runVariableBounds(
            """
                inlined oxsts of semantifyr::Anything
                var divisor : int := 0
                var quotient : int := 0
                init { }
                tran {
                    choice { divisor := 0 } or { divisor := 2 }
                    quotient := 10 / divisor
                }
                prop { AG (quotient <= 10) }
            """,
        )

        assertThat(result.variableRanges[inlined.varNamed("quotient")]).isEqualTo(IntegerRange.UNBOUNDED)
    }

    @Test
    fun `code no run reaches records nothing`() {
        val (inlined, result) = runVariableBounds(
            """
                inlined oxsts of semantifyr::Anything
                var counter : int := 0
                init { }
                tran {
                    assume(counter > 0)
                    counter := counter * 2
                }
                prop { AG (counter == 0) }
            """,
        )

        assertThat(result.variableRanges[inlined.varNamed("counter")]).isEqualTo(IntegerRange(0, 0))
        assertThat(result.arithmeticRanges).isEmpty()
    }

    @Test
    fun `a variable copied from a bounded one is bounded`() {
        val (inlined, result) = runVariableBounds(
            """
                inlined oxsts of semantifyr::Anything
                var counter : int := 0
                var doubled : int := 0
                init { }
                tran {
                    if (counter < 10) { counter := counter + 1 } else { counter := 0 }
                    doubled := counter * 2
                }
                prop { AG (doubled <= 20) }
            """,
        )

        assertThat(result.variableRanges[inlined.varNamed("doubled")]).isEqualTo(IntegerRange(0, 20))
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
