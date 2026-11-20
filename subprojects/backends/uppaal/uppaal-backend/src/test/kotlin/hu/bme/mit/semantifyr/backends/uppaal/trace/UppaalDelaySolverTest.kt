/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.uppaal.trace

import hu.bme.mit.semantifyr.backend.witness.Fraction
import hu.bme.mit.semantifyr.backend.witness.WitnessFailedException
import hu.bme.mit.semantifyr.backends.uppaal.transformation.UppaalClockAtom
import hu.bme.mit.semantifyr.backends.uppaal.transformation.UppaalClockEdge
import hu.bme.mit.semantifyr.backends.uppaal.transformation.UppaalClockOp
import hu.bme.mit.semantifyr.compiler.pipeline.utils.OxstsFactory
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class UppaalDelaySolverTest {

    private val solver = UppaalDelaySolver()

    private fun clock(name: String): VariableDeclaration {
        return OxstsFactory.createVariableDeclaration().also {
            it.name = name
        }
    }

    private fun test(
        clock: VariableDeclaration,
        operator: UppaalClockOp,
        bound: Long,
    ): UppaalClockEdge {
        return UppaalClockEdge.Test(listOf(UppaalClockAtom(clock, operator, bound)))
    }

    private fun reset(clock: VariableDeclaration): UppaalClockEdge {
        return UppaalClockEdge.Reset(clock)
    }

    @Test
    fun `a path without clock tests waits no time`() {
        val elapsed = clock("elapsed")

        val states = solver.solve(listOf(elapsed), listOf(emptyList(), emptyList(), emptyList()))

        assertThat(
            states.map {
                it.elapsed
            },
        ).containsOnly(Fraction.ZERO)
    }

    @Test
    fun `a lower bound waits until it holds`() {
        val elapsed = clock("elapsed")

        val states = solver.solve(listOf(elapsed), listOf(emptyList(), listOf(test(elapsed, UppaalClockOp.GreaterEqual, 6))))

        assertThat(states[1].elapsed).isEqualTo(Fraction.of(6))
        assertThat(states[1].clockValues.single().value).isEqualTo(Fraction.of(6))
    }

    @Test
    fun `strict bounds of one unit are met halfway on a grid of one half`() {
        val elapsed = clock("elapsed")

        val states = solver.solve(
            listOf(elapsed),
            listOf(
                emptyList(),
                listOf(
                    test(elapsed, UppaalClockOp.Greater, 0),
                    test(elapsed, UppaalClockOp.Less, 1),
                    reset(elapsed),
                ),
            ),
        )

        assertThat(states[1].elapsed).isEqualTo(Fraction.of(1, 2))
        assertThat(states[1].clockValues.single().value).isEqualTo(Fraction.ZERO)
    }

    @Test
    fun `a clock read after its reset in the same step reads zero and passes no time`() {
        val lap = clock("lap")
        val total = clock("total")

        val states = solver.solve(
            listOf(lap, total),
            listOf(
                emptyList(),
                listOf(
                    test(total, UppaalClockOp.GreaterEqual, 1),
                    reset(lap),
                ),
                listOf(
                    test(lap, UppaalClockOp.LessEqual, 0),
                ),
            ),
        )

        assertThat(states[2].elapsed).isEqualTo(Fraction.ZERO)
        assertThat(
            states[2].clockValues.map {
                it.value
            },
        ).containsExactly(Fraction.ZERO, Fraction.of(1))
    }

    @Test
    fun `n strict steps within one unit lie on a grid of one over n plus one`() {
        val outer = clock("outer")
        val inner = clock("inner")
        val step = listOf(
            test(outer, UppaalClockOp.Less, 1),
            test(inner, UppaalClockOp.Greater, 0),
            reset(inner),
        )

        val states = solver.solve(listOf(outer, inner), listOf(emptyList(), step, step, step))

        assertThat(
            states.drop(1).map {
                it.elapsed
            },
        ).containsOnly(Fraction.of(1, 4))
    }

    @Test
    fun `a path its clock tests contradict fails the read-back`() {
        val elapsed = clock("elapsed")

        assertThrows<WitnessFailedException> {
            solver.solve(
                listOf(elapsed),
                listOf(
                    emptyList(),
                    listOf(
                        test(elapsed, UppaalClockOp.LessEqual, 5),
                        test(elapsed, UppaalClockOp.GreaterEqual, 6),
                    ),
                ),
            )
        }
    }

    @Test
    fun `init passes no time, so a test in init sees every clock at zero`() {
        val elapsed = clock("elapsed")

        assertThrows<WitnessFailedException> {
            solver.solve(listOf(elapsed), listOf(listOf(test(elapsed, UppaalClockOp.GreaterEqual, 1))))
        }
    }
}
