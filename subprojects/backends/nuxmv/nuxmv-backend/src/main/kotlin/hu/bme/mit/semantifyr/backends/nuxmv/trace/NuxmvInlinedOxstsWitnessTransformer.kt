/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.nuxmv.trace

import com.google.inject.Inject
import hu.bme.mit.semantifyr.backend.witness.Fraction
import hu.bme.mit.semantifyr.backend.witness.InlinedWitness
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
import hu.bme.mit.semantifyr.compiler.pipeline.utils.eAllOfType
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
        val nameToVariable = inlinedOxsts.eAllOfType<VariableDeclaration>().associateBy {
            nuxmvVariableTransformer.nameOf(it)
        }
        val stateVariables = inlinedOxsts.variables.toSet()
        val clocks = inlinedOxsts.variables.filter {
            nuxmvVariableTransformer.isClock(it)
        }

        val running = mutableMapOf<VariableDeclaration, Expression>()
        val runningClocks = mutableMapOf<VariableDeclaration, Fraction>()
        val updated = mutableSetOf<VariableDeclaration>()

        val states = trace.states.mapIndexed { index, state ->
            for ((name, raw) in state.variableValues) {
                if (name in auxiliaryNames) {
                    continue
                }
                val variable = nameToVariable[name] ?: throw WitnessFailedException("nuXmv's trace holds $name, which names no variable of the model")
                if (variable !in stateVariables) {
                    continue
                }
                if (nuxmvVariableTransformer.isClock(variable)) {
                    runningClocks[variable] = parseReal(raw) ?: throw WitnessFailedException("nuXmv's trace holds $raw for $name, which is no value of a clock")
                    continue
                }
                running[variable] = parseValue(variable, raw) ?: throw WitnessFailedException("nuXmv's trace holds $raw for $name, which is no value of its type")
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
        val negative = raw.startsWith("-")
        val magnitude = raw.removePrefix("-")
        val value = if (magnitude.startsWith("f'")) {
            parseFraction(magnitude.removePrefix("f'"))
        } else {
            parseDecimal(magnitude)
        } ?: return null
        if (negative) {
            return Fraction.of(value.numerator.negate(), value.denominator)
        }
        return value
    }

    private fun parseFraction(text: String): Fraction? {
        return try {
            Fraction.parse(text)
        } catch (_: NumberFormatException) {
            null
        }
    }

    private fun parseDecimal(text: String): Fraction? {
        val decimal = text.toBigDecimalOrNull() ?: return null
        if (decimal.scale() <= 0) {
            return Fraction.of(decimal.toBigIntegerExact())
        }
        return Fraction.of(decimal.unscaledValue(), BigInteger.TEN.pow(decimal.scale()))
    }

    private fun parseValue(variable: VariableDeclaration, raw: String): Expression? {
        val kind = nuxmvVariableTransformer.describe(variable).kind
        return when (kind) {
            NuxmvVariableKind.Clock -> error("a clock's value is a fraction, read apart from the state values")
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
        }
    }
}
