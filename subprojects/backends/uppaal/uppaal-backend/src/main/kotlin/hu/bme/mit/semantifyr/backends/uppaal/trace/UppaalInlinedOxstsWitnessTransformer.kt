/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.uppaal.trace

import com.google.inject.Inject
import hu.bme.mit.semantifyr.backend.witness.InlinedWitness
import hu.bme.mit.semantifyr.backend.witness.WitnessState
import hu.bme.mit.semantifyr.backend.witness.WitnessStateValue
import hu.bme.mit.semantifyr.backends.uppaal.transformation.UppaalVariableKind
import hu.bme.mit.semantifyr.backends.uppaal.transformation.UppaalVariableTransformer
import hu.bme.mit.semantifyr.compiler.pipeline.utils.OxstsFactory
import hu.bme.mit.semantifyr.compiler.pipeline.utils.copy
import hu.bme.mit.semantifyr.oxsts.model.oxsts.EnumDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.EnumLiteral
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration

class UppaalInlinedOxstsWitnessTransformer @Inject constructor(
    private val uppaalVariableTransformer: UppaalVariableTransformer,
) {

    fun transform(inlinedOxsts: InlinedOxsts, trace: UppaalTrace): InlinedWitness {
        val nameToVariable = inlinedOxsts.variables.associateBy {
            uppaalVariableTransformer.nameOf(it)
        }
        val boundaryStates = trace.states.filter {
            it.isOxstsBoundary()
        }

        val running = mutableMapOf<VariableDeclaration, Expression>()
        val updated = mutableSetOf<VariableDeclaration>()

        val states = boundaryStates.map {
            updateRunningValues(it, nameToVariable, running, updated)
            buildWitnessState(inlinedOxsts.variables, running, updated)
        }

        require(states.isNotEmpty()) {
            "Cannot build witness from an empty Uppaal trace"
        }

        return assembleWitness(states, inlinedOxsts)
    }

    private fun updateRunningValues(
        state: UppaalTraceState,
        nameToVariable: Map<String, VariableDeclaration>,
        running: MutableMap<VariableDeclaration, Expression>,
        updated: MutableSet<VariableDeclaration>,
    ) {
        for ((name, raw) in state.variableValues) {
            val variable = nameToVariable[name] ?: continue
            val value = parseValue(variable, raw) ?: continue
            running[variable] = value
            updated += variable
        }
    }

    private fun buildWitnessState(
        variables: List<VariableDeclaration>,
        running: Map<VariableDeclaration, Expression>,
        updated: Set<VariableDeclaration>,
    ): WitnessState {
        val values = variables.mapNotNull { variable ->
            if (variable !in updated) {
                return@mapNotNull null
            }
            running[variable]?.let {
                WitnessStateValue(variable, it.copy())
            }
        }
        return WitnessState(values)
    }

    private fun assembleWitness(states: List<WitnessState>, inlinedOxsts: InlinedOxsts): InlinedWitness {
        val initial = states[0]
        val initialized = states.getOrNull(1)
        val transitions = if (initialized != null) {
            states.drop(2)
        } else {
            emptyList()
        }
        val nextStateMap = buildNextStateMap(initialized, transitions)
        return InlinedWitness(initial, initialized, transitions, nextStateMap, inlinedOxsts)
    }

    private fun buildNextStateMap(
        initialized: WitnessState?,
        transitions: List<WitnessState>,
    ): Map<WitnessState, List<WitnessState>> {
        val map = mutableMapOf<WitnessState, List<WitnessState>>()
        if (initialized != null && transitions.isNotEmpty()) {
            map[initialized] = listOf(transitions.first())
        }
        for (i in 1 until transitions.size) {
            map[transitions[i - 1]] = listOf(transitions[i])
        }
        return map
    }

    private fun UppaalTraceState.isOxstsBoundary(): Boolean {
        return locations.any {
            it.endsWith(".Start") || it.endsWith(".Running")
        }
    }

    private fun parseValue(variable: VariableDeclaration, raw: String): Expression? {
        val kind = uppaalVariableTransformer.describe(variable).kind
        return when (kind) {
            UppaalVariableKind.Integer -> raw.toIntOrNull()?.let {
                OxstsFactory.createLiteralInteger(it)
            }
            UppaalVariableKind.Boolean -> parseBooleanValue(raw)
            UppaalVariableKind.Enum -> parseEnumValue(variable, raw)
            UppaalVariableKind.Clock -> null
        }
    }

    private fun parseBooleanValue(raw: String): Expression? {
        return when (raw) {
            "0" -> OxstsFactory.createLiteralBoolean(false)
            "1" -> OxstsFactory.createLiteralBoolean(true)
            else -> null
        }
    }

    private fun parseEnumValue(variable: VariableDeclaration, raw: String): Expression? {
        val enumDeclaration = variable.typeSpecification?.domain as? EnumDeclaration ?: return null
        val literal = findLiteralByOrdinal(enumDeclaration, raw) ?: return null
        return OxstsFactory.createElementReference(literal)
    }

    // Uppaal encodes enum literals as their integer ordinal; map back via declaration order.
    private fun findLiteralByOrdinal(enumDeclaration: EnumDeclaration, raw: String): EnumLiteral? {
        val ordinal = raw.toIntOrNull() ?: return null
        return enumDeclaration.literals.getOrNull(ordinal)
    }
}
