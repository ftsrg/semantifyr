/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.nuxmv.trace

import com.google.inject.Inject
import hu.bme.mit.semantifyr.backend.witness.Fraction
import hu.bme.mit.semantifyr.backend.witness.InlinedWitness
import hu.bme.mit.semantifyr.backend.witness.TraceVariableResolver
import hu.bme.mit.semantifyr.backend.witness.WitnessClockValue
import hu.bme.mit.semantifyr.backend.witness.WitnessFailedException
import hu.bme.mit.semantifyr.backend.witness.WitnessState
import hu.bme.mit.semantifyr.backend.witness.WitnessStateValue
import hu.bme.mit.semantifyr.backend.witness.linearInlinedWitness
import hu.bme.mit.semantifyr.backends.nuxmv.transformation.NUXMV_DELAY_NAME
import hu.bme.mit.semantifyr.backends.nuxmv.transformation.NuxmvVariableKind
import hu.bme.mit.semantifyr.backends.nuxmv.transformation.NuxmvVariableTransformer
import hu.bme.mit.semantifyr.compiler.pipeline.utils.OxstsFactory
import hu.bme.mit.semantifyr.compiler.pipeline.utils.copy
import hu.bme.mit.semantifyr.oxsts.model.oxsts.EnumDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration
import java.math.BigInteger

class NuxmvInlinedOxstsWitnessTransformer @Inject constructor(
    private val nuxmvVariableTransformer: NuxmvVariableTransformer,
) {

    fun transform(
        inlinedOxsts: InlinedOxsts,
        trace: NuxmvTrace,
        auxiliaryNames: Set<String>,
    ): InlinedWitness {
        val resolver = TraceVariableResolver(inlinedOxsts, nuxmvVariableTransformer, auxiliaryNames, "nuXmv's trace")
        val clocks = inlinedOxsts.variables.filter {
            nuxmvVariableTransformer.kindOf(it) == NuxmvVariableKind.Clock
        }

        val running = mutableMapOf<VariableDeclaration, Expression>()
        val runningClocks = mutableMapOf<VariableDeclaration, Fraction>()
        val updated = mutableSetOf<VariableDeclaration>()

        val states = trace.states.mapIndexed { index, state ->
            for ((name, raw) in state.variableValues) {
                val variable = resolver.stateVariableOf(name) ?: continue
                val kind = nuxmvVariableTransformer.kindOf(variable) ?: error("The variable $name has no nuXmv kind")
                if (kind == NuxmvVariableKind.Clock) {
                    runningClocks[variable] = parseReal(raw) ?: throw WitnessFailedException("nuXmv's trace holds $raw for $name, which is no value of a clock")
                    continue
                }
                running[variable] = parseValue(variable, kind, raw) ?: throw WitnessFailedException("nuXmv's trace holds $raw for $name, which is no value of its type")
                updated += variable
            }
            val values = inlinedOxsts.variables.mapNotNull { variable ->
                if (variable !in updated) {
                    return@mapNotNull null
                }
                running[variable]?.let {
                    WitnessStateValue(variable, it.copy())
                }
            }
            val clockValues = clocks.map {
                val value = runningClocks[it] ?: throw WitnessFailedException("nuXmv's trace holds no value for the clock ${nuxmvVariableTransformer.nameOf(it)}")
                WitnessClockValue(it, value)
            }
            WitnessState(values, clockValues, elapsedOf(index, state, clocks))
        }

        return linearInlinedWitness(inlinedOxsts, states)
    }

    private fun elapsedOf(
        index: Int,
        state: NuxmvTraceState,
        clocks: List<VariableDeclaration>,
    ): Fraction {
        if (clocks.isEmpty() || index == 0) {
            return Fraction.ZERO
        }
        val raw = state.inputValues[NUXMV_DELAY_NAME] ?: throw WitnessFailedException("nuXmv's trace holds no delay for step $index")
        return parseReal(raw) ?: throw WitnessFailedException("nuXmv's trace holds $raw for the delay of step $index, which is no amount of time")
    }

    private fun parseReal(raw: String): Fraction? {
        val whole = WHOLE_REAL.matchEntire(raw)
        if (whole != null) {
            return Fraction.of(BigInteger(whole.groupValues[1]))
        }
        val fraction = FRACTION_REAL.matchEntire(raw) ?: return null
        return Fraction.of(BigInteger(fraction.groupValues[1] + fraction.groupValues[2]), BigInteger(fraction.groupValues[3]))
    }

    private fun parseValue(
        variable: VariableDeclaration,
        kind: NuxmvVariableKind,
        raw: String,
    ): Expression? {
        return when (kind) {
            NuxmvVariableKind.Integer -> raw.toIntOrNull()?.let {
                OxstsFactory.createLiteralInteger(it)
            }
            NuxmvVariableKind.Boolean -> when (raw.uppercase()) {
                "TRUE" -> OxstsFactory.createLiteralBoolean(true)
                "FALSE" -> OxstsFactory.createLiteralBoolean(false)
                else -> null
            }
            NuxmvVariableKind.Enum -> {
                val enumDeclaration = variable.typeSpecification?.domain as? EnumDeclaration ?: return null
                val literal = enumDeclaration.literals.firstOrNull {
                    nuxmvVariableTransformer.nameOf(it) == raw
                } ?: return null
                OxstsFactory.createElementReference(literal)
            }
            NuxmvVariableKind.Clock -> null
        }
    }
}

// the forms nuXmv prints a real in: a whole one as 2.0 or -1.0, any other as f'17/6 or -f'3/2
private val WHOLE_REAL = Regex("(-?[0-9]+)\\.0")
private val FRACTION_REAL = Regex("(-?)f'([0-9]+)/([1-9][0-9]*)")
