/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.uppaal.trace

import hu.bme.mit.semantifyr.backend.witness.Fraction
import hu.bme.mit.semantifyr.backend.witness.WitnessClockValue
import hu.bme.mit.semantifyr.backend.witness.WitnessFailedException
import hu.bme.mit.semantifyr.backends.uppaal.transformation.UppaalClockEdge
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ComparisonOp
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration

class UppaalTimedState(
    val elapsed: Fraction,
    val clockValues: List<WitnessClockValue>,
)

private class DifferenceConstraint(
    val later: Int,
    val earlier: Int,
    val op: ComparisonOp,
    val bound: Long,
)

// a clock read is the step's instant minus that of its last reset, so the path's tests are difference constraints over the instants, solved on the coarsest grid of 1/q up to the path's length plus one with a strict test given one grain of slack
class UppaalDelaySolver {

    fun solve(clocks: List<VariableDeclaration>, steps: List<List<UppaalClockEdge>>): List<UppaalTimedState> {
        val constraints = mutableListOf<DifferenceConstraint>()
        val resetsAfter = mutableListOf<Map<VariableDeclaration, Int>>()
        val lastReset = clocks.associateWith {
            0
        }.toMutableMap()
        for ((step, edges) in steps.withIndex()) {
            for (edge in edges) {
                for (atom in edge.clockAtoms) {
                    val reset = lastReset[atom.clock] ?: 0
                    if (reset == step) {
                        if (!atom.holdsAt(0)) {
                            throw WitnessFailedException("Uppaal's path fails a test of a clock reset in the same step")
                        }
                    } else {
                        constraints += DifferenceConstraint(step, reset, atom.op, atom.bound)
                    }
                }
                for (clock in edge.resets) {
                    lastReset[clock] = step
                }
            }
            resetsAfter += lastReset.toMap()
        }
        val instants = (1..steps.size).firstNotNullOfOrNull {
            earliestInstants(steps.size, constraints, it.toLong())
        } ?: throw WitnessFailedException("Uppaal's path admits no delays on a grid of 1/${steps.size}")
        return steps.indices.map { step ->
            val elapsed = if (step == 0) {
                Fraction.ZERO
            } else {
                Fraction.of(instants.values[step] - instants.values[step - 1], instants.grain)
            }
            val clockValues = clocks.map {
                val reset = resetsAfter[step].getValue(it)
                WitnessClockValue(it, Fraction.of(instants.values[step] - instants.values[reset], instants.grain))
            }
            UppaalTimedState(elapsed, clockValues)
        }
    }

    private class Instants(
        val values: LongArray,
        val grain: Long,
    )

    private class LowerBound(
        val from: Int,
        val to: Int,
        val weight: Long,
    )

    // instant[to] >= instant[from] + weight, in units of 1/grain, solved as a longest path from the initial instant
    private fun earliestInstants(
        count: Int,
        constraints: List<DifferenceConstraint>,
        grain: Long,
    ): Instants? {
        val lowerBounds = mutableListOf<LowerBound>()
        for (step in 1..<count) {
            lowerBounds += LowerBound(step - 1, step, 0)
        }
        for (constraint in constraints) {
            val scaled = constraint.bound * grain
            val later = constraint.later
            val earlier = constraint.earlier
            when (constraint.op) {
                ComparisonOp.LESS_EQ -> lowerBounds += LowerBound(later, earlier, -scaled)
                ComparisonOp.LESS -> lowerBounds += LowerBound(later, earlier, 1 - scaled)
                ComparisonOp.GREATER_EQ -> lowerBounds += LowerBound(earlier, later, scaled)
                ComparisonOp.GREATER -> lowerBounds += LowerBound(earlier, later, scaled + 1)
                ComparisonOp.EQ -> {
                    lowerBounds += LowerBound(later, earlier, -scaled)
                    lowerBounds += LowerBound(earlier, later, scaled)
                }
                ComparisonOp.NOT_EQ -> error("Uppaal's guards hold no clock disequality")
            }
        }
        val values = LongArray(count)
        repeat(count + 1) {
            var changed = false
            for (lowerBound in lowerBounds) {
                val candidate = values[lowerBound.from] + lowerBound.weight
                if (candidate > values[lowerBound.to]) {
                    values[lowerBound.to] = candidate
                    changed = true
                }
            }
            if (!changed) {
                if (values[0] != 0L) {
                    return null
                }
                return Instants(values, grain)
            }
        }
        return null
    }
}
