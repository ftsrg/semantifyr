/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.uppaal.trace

import com.google.inject.Inject
import hu.bme.mit.semantifyr.backend.witness.InlinedWitness
import hu.bme.mit.semantifyr.backend.witness.TraceVariableResolver
import hu.bme.mit.semantifyr.backend.witness.WitnessFailedException
import hu.bme.mit.semantifyr.backend.witness.WitnessState
import hu.bme.mit.semantifyr.backend.witness.WitnessStateValue
import hu.bme.mit.semantifyr.backend.witness.linearInlinedWitness
import hu.bme.mit.semantifyr.backends.uppaal.transformation.UppaalClockEdge
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

    private val uppaalDelaySolver = UppaalDelaySolver()

    fun transform(
        inlinedOxsts: InlinedOxsts,
        trace: UppaalTrace,
        clockEdges: Map<String, UppaalClockEdge>,
    ): InlinedWitness {
        val resolver = TraceVariableResolver(inlinedOxsts, uppaalVariableTransformer, emptySet(), "Uppaal's trace")
        val clocks = inlinedOxsts.variables.filter {
            uppaalVariableTransformer.kindOf(it) == UppaalVariableKind.Clock
        }

        val running = mutableMapOf<VariableDeclaration, Expression>()
        val updated = mutableSetOf<VariableDeclaration>()

        val stableValues = mutableListOf<List<WitnessStateValue>>()
        val steps = mutableListOf<List<UppaalClockEdge>>()
        var stepEdges = mutableListOf<UppaalClockEdge>()
        for (state in trace.states) {
            state.locations.firstNotNullOfOrNull {
                clockEdges[it.substringAfter('.')]
            }?.let {
                stepEdges += it
            }
            if (!state.isOxstsBoundary()) {
                continue
            }
            updateRunningValues(state, resolver, running, updated)
            if (state.isStable()) {
                stableValues += valuesOf(inlinedOxsts.variables, running, updated)
                steps += stepEdges
                stepEdges = mutableListOf()
            }
        }

        if (stableValues.isEmpty()) {
            throw WitnessFailedException("Uppaal's trace has no initialized state")
        }

        val timedStates = uppaalDelaySolver.solve(clocks, steps)
        val states = stableValues.zip(timedStates) { values, timed ->
            WitnessState(values, timed.clockValues, timed.elapsed)
        }
        return linearInlinedWitness(inlinedOxsts, states)
    }

    private fun updateRunningValues(
        state: UppaalTraceState,
        resolver: TraceVariableResolver,
        running: MutableMap<VariableDeclaration, Expression>,
        updated: MutableSet<VariableDeclaration>,
    ) {
        for ((name, raw) in state.variableValues) {
            val variable = resolver.stateVariableOf(name) ?: continue
            val kind = uppaalVariableTransformer.kindOf(variable)
            if (kind == UppaalVariableKind.Clock) {
                continue
            }
            running[variable] = parseValue(variable, kind, raw) ?: throw WitnessFailedException("Uppaal's trace holds $raw for $name, which is no value of its type")
            updated += variable
        }
    }

    private fun valuesOf(
        variables: List<VariableDeclaration>,
        running: Map<VariableDeclaration, Expression>,
        updated: Set<VariableDeclaration>,
    ): List<WitnessStateValue> {
        return variables.mapNotNull { variable ->
            if (variable !in updated) {
                return@mapNotNull null
            }
            running[variable]?.let {
                WitnessStateValue(variable, it.copy())
            }
        }
    }

    private fun UppaalTraceState.isOxstsBoundary(): Boolean {
        return locations.any {
            it.endsWith(".Start") || it.endsWith(".Running")
        }
    }

    private fun UppaalTraceState.isStable(): Boolean {
        return locations.any {
            it.endsWith(".Running")
        }
    }

    private fun parseValue(
        variable: VariableDeclaration,
        kind: UppaalVariableKind?,
        raw: String,
    ): Expression? {
        return when (kind) {
            UppaalVariableKind.Integer -> raw.toIntOrNull()?.let {
                OxstsFactory.createLiteralInteger(it)
            }
            UppaalVariableKind.Boolean -> parseBooleanValue(raw)
            UppaalVariableKind.Enum -> parseEnumValue(variable, raw)
            UppaalVariableKind.Clock, null -> null
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
