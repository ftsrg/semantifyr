/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backend.transformation

import com.google.inject.Inject
import hu.bme.mit.semantifyr.backend.SoundnessDirection
import hu.bme.mit.semantifyr.backend.witness.Fraction
import hu.bme.mit.semantifyr.backend.witness.InlinedWitness
import hu.bme.mit.semantifyr.backend.witness.WitnessClockValue
import hu.bme.mit.semantifyr.backend.witness.WitnessFailedException
import hu.bme.mit.semantifyr.backend.witness.WitnessState
import hu.bme.mit.semantifyr.backend.witness.WitnessStateValue
import hu.bme.mit.semantifyr.compiler.pipeline.utils.eAllOfType
import hu.bme.mit.semantifyr.oxsts.lang.semantics.clock.ClockTestAnalysis
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.ConstantExpressionEvaluatorProvider
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.IntegerEvaluation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ComparisonOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralInteger
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration

const val WHOLE_DELAY_VARIABLE_NAME = "__semantifyr_delay"

class WholeDelayClock(
    val variable: VariableDeclaration,
    val saturation: Long,
)

class WholeDelay(
    val clocks: List<WholeDelayClock>,
) {
    val longestDelay = clocks.maxOf {
        it.saturation
    }

    fun restore(witness: InlinedWitness): InlinedWitness {
        val restored = mutableMapOf<WitnessState, WitnessState>()
        val initializedState = restoreState(witness.initializedState, null)
        restored[witness.initializedState] = initializedState
        val pending = ArrayDeque(listOf(witness.initializedState))
        while (pending.isNotEmpty()) {
            val state = pending.removeFirst()
            for (next in witness.getNextStates(state)) {
                if (next !in restored) {
                    restored[next] = restoreState(next, restored.getValue(state))
                    pending += next
                }
            }
        }
        val transitionStates = witness.transitionStates.map {
            restored[it] ?: restoreState(it, null)
        }
        val nextStateMap = witness.nextStateMap.map { (state, nextStates) ->
            restored.getValue(state) to nextStates.map {
                restored.getValue(it)
            }
        }.toMap()
        return InlinedWitness(
            initializedState = initializedState,
            transitionStates = transitionStates,
            nextStateMap = nextStateMap,
            inlinedOxsts = witness.inlinedOxsts,
        )
    }

    private fun restoreState(state: WitnessState, previous: WitnessState?): WitnessState {
        val saturations = clocks.associate {
            it.variable to it.saturation
        }
        val previousValues = previous?.clockValues.orEmpty().associate {
            it.variable to it.value
        }
        val clockValues = mutableListOf<WitnessClockValue>()
        val values = mutableListOf<WitnessStateValue>()
        for (value in state.values) {
            val saturation = saturations[value.variable]
            if (saturation == null) {
                values += value
                continue
            }
            val raw = (value.value as? LiteralInteger)?.value
                ?: throw WitnessFailedException("the clock ${value.variable.name} has no whole value in the trace")
            clockValues += WitnessClockValue(value.variable, clockValue(value.variable, raw, saturation, previousValues, state.elapsed))
        }
        return WitnessState(values, clockValues, state.elapsed)
    }

    private fun clockValue(
        variable: VariableDeclaration,
        raw: Int,
        saturation: Long,
        previousValues: Map<VariableDeclaration, Fraction>,
        elapsed: Fraction,
    ): Fraction {
        if (raw < 0 || raw > saturation) {
            throw WitnessFailedException("the clock ${variable.name} holds $raw in the trace, beyond its saturation $saturation")
        }
        if (raw < saturation) {
            return Fraction.of(raw.toBigInteger())
        }
        val previous = previousValues[variable]
            ?: throw WitnessFailedException("the clock ${variable.name} is saturated in the trace with no value before it")
        return previous + elapsed
    }
}

class WholeDelayLowering @Inject constructor(
    private val clockTestAnalysis: ClockTestAnalysis,
    private val constantExpressionEvaluatorProvider: ConstantExpressionEvaluatorProvider,
) {

    fun lower(inlinedOxsts: InlinedOxsts): WholeDelay? {
        val clocks = inlinedOxsts.variables.filter {
            clockTestAnalysis.isClock(it)
        }
        if (clocks.isEmpty()) {
            return null
        }
        val largestBounds = mutableMapOf<VariableDeclaration, Int>()
        for (comparison in inlinedOxsts.eAllOfType<ComparisonOperator>()) {
            val test = clockTestAnalysis.clockTest(comparison) ?: continue
            val evaluation = constantExpressionEvaluatorProvider.evaluate(test.bound) as? IntegerEvaluation
                ?: error("A clock bound is no compile-time integer")
            for (leaf in test.clockLeaves.orEmpty()) {
                val clock = clockTestAnalysis.referencedClock(leaf)
                largestBounds[clock] = maxOf(largestBounds[clock] ?: 0, evaluation.value)
            }
        }
        return WholeDelay(
            clocks.map {
                WholeDelayClock(it, (largestBounds[it] ?: 0) + 1L)
            },
        )
    }

    fun soundness(inlinedOxsts: InlinedOxsts, configured: SoundnessDirection): SoundnessDirection {
        if (clockTestAnalysis.unclosedTests(inlinedOxsts.mainTransition).isEmpty()) {
            return configured
        }
        return SoundnessDirection.UnderApproximating
    }
}
