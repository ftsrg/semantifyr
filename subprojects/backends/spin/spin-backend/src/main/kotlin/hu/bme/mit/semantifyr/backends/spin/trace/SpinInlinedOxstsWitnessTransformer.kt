/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.spin.trace

import com.google.inject.Inject
import hu.bme.mit.semantifyr.backend.witness.InlinedWitness
import hu.bme.mit.semantifyr.backend.witness.WitnessState
import hu.bme.mit.semantifyr.backend.witness.WitnessStateValue
import hu.bme.mit.semantifyr.backend.witness.linearInlinedWitness
import hu.bme.mit.semantifyr.backends.spin.transformation.SPIN_STABLE_FLAG
import hu.bme.mit.semantifyr.backends.spin.transformation.SpinVariableKind
import hu.bme.mit.semantifyr.backends.spin.transformation.SpinVariableTransformer
import hu.bme.mit.semantifyr.compiler.pipeline.utils.OxstsFactory
import hu.bme.mit.semantifyr.compiler.pipeline.utils.copy
import hu.bme.mit.semantifyr.oxsts.model.oxsts.EnumDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.EnumLiteral
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration

class SpinInlinedOxstsWitnessTransformer @Inject constructor(
    private val spinVariableTransformer: SpinVariableTransformer,
) {

    fun transform(inlinedOxsts: InlinedOxsts, trace: SpinTrace): InlinedWitness {
        val nameToVar = inlinedOxsts.variables.associateBy {
            spinVariableTransformer.nameOf(it)
        }

        // the replay prints a global only when it changes, so every value starts from its declaration
        val running = mutableMapOf<VariableDeclaration, Expression>()
        for (variable in inlinedOxsts.variables) {
            declaredValue(variable)?.let {
                running[variable] = it
            }
        }

        val states = trace.states.mapNotNull { state ->
            for ((name, raw) in state.variableValues) {
                val variable = nameToVar[name] ?: continue
                val value = parseValue(variable, raw) ?: continue
                running[variable] = value
            }
            val stable = state.variableValues[SPIN_STABLE_FLAG]
            if (stable != "1" && stable != "true") {
                return@mapNotNull null
            }
            val values = inlinedOxsts.variables.mapNotNull { variable ->
                running[variable]?.let {
                    WitnessStateValue(variable, it.copy())
                }
            }
            WitnessState(values)
        }

        return linearInlinedWitness(inlinedOxsts, states)
    }

    // Promela starts an uninitialized int or bool at 0, an uninitialized mtype is always havoced
    private fun declaredValue(variable: VariableDeclaration): Expression? {
        val initializer = variable.expression
        if (initializer != null) {
            return initializer.copy()
        }
        return when (spinVariableTransformer.describe(variable).kind) {
            SpinVariableKind.Integer -> OxstsFactory.createLiteralInteger(0)
            SpinVariableKind.Boolean -> OxstsFactory.createLiteralBoolean(false)
            SpinVariableKind.Enum -> null
        }
    }

    private fun parseValue(variable: VariableDeclaration, raw: String): Expression? {
        val kind = spinVariableTransformer.describe(variable).kind
        return when (kind) {
            SpinVariableKind.Integer -> raw.toIntOrNull()?.let {
                OxstsFactory.createLiteralInteger(it)
            }
            SpinVariableKind.Boolean -> when (raw) {
                "0", "false" -> OxstsFactory.createLiteralBoolean(false)
                "1", "true" -> OxstsFactory.createLiteralBoolean(true)
                else -> null
            }
            SpinVariableKind.Enum -> parseEnumValue(variable, raw)
        }
    }

    private fun parseEnumValue(variable: VariableDeclaration, raw: String): Expression? {
        val enumDeclaration = variable.typeSpecification?.domain as? EnumDeclaration ?: return null
        val literal = findLiteralByName(enumDeclaration, raw)
            ?: findLiteralByOrdinal(enumDeclaration, raw)
            ?: return null
        return OxstsFactory.createElementReference(literal)
    }

    private fun findLiteralByName(enumDeclaration: EnumDeclaration, raw: String): EnumLiteral? {
        return enumDeclaration.literals.firstOrNull {
            spinVariableTransformer.nameOf(it) == raw
        }
    }

    private fun findLiteralByOrdinal(enumDeclaration: EnumDeclaration, raw: String): EnumLiteral? {
        val ordinal = raw.toIntOrNull() ?: return null
        return enumDeclaration.literals.getOrNull(ordinal)
    }
}
