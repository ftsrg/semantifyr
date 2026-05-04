/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.uppaal.trace

import com.google.inject.Inject
import hu.bme.mit.semantifyr.backend.scopes.VerificationScoped
import hu.bme.mit.semantifyr.backend.witness.InlinedWitness
import hu.bme.mit.semantifyr.backend.witness.WitnessState
import hu.bme.mit.semantifyr.backend.witness.WitnessStateValue
import hu.bme.mit.semantifyr.backends.uppaal.transformation.UppaalVariableKind
import hu.bme.mit.semantifyr.backends.uppaal.transformation.UppaalVariableTransformer
import hu.bme.mit.semantifyr.compiler.pipeline.utils.OxstsFactory
import hu.bme.mit.semantifyr.compiler.pipeline.utils.copy
import hu.bme.mit.semantifyr.oxsts.model.oxsts.EnumDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration

@VerificationScoped
class UppaalInlinedOxstsWitnessTransformer @Inject constructor(
    private val variableTransformer: UppaalVariableTransformer,
) {

    fun transform(inlinedOxsts: InlinedOxsts, trace: UppaalTrace): InlinedWitness {
        val nameToVar = inlinedOxsts.variables.associateBy { variableTransformer.nameOf(it) }

        val boundaryStates = trace.states.filter { it.isOxstsBoundary() }

        val running = mutableMapOf<VariableDeclaration, Expression>()
        val updated = mutableSetOf<VariableDeclaration>()

        val states = boundaryStates.map { state ->
            for ((name, raw) in state.variableValues) {
                val variable = nameToVar[name] ?: continue
                val value = parseValue(variable, raw) ?: continue
                running[variable] = value
                updated += variable
            }
            val values = inlinedOxsts.variables.mapNotNull { variable ->
                if (variable !in updated) {
                    return@mapNotNull null
                }
                running[variable]?.let { WitnessStateValue(variable, it.copy()) }
            }
            WitnessState(values)
        }

        require(states.isNotEmpty()) { "Cannot build witness from an empty Uppaal trace" }

        val initial = states[0]
        val initialized = states.getOrNull(1)
        val transitions = if (initialized != null) states.drop(2) else emptyList()

        val nextStateMap = mutableMapOf<WitnessState, List<WitnessState>>()
        if (initialized != null && transitions.isNotEmpty()) {
            nextStateMap[initialized] = listOf(transitions.first())
        }
        for (i in 1 until transitions.size) {
            nextStateMap[transitions[i - 1]] = listOf(transitions[i])
        }

        return InlinedWitness(
            initialState = initial,
            initializedState = initialized,
            transitionStates = transitions,
            nextStateMap = nextStateMap,
            inlinedOxsts = inlinedOxsts,
        )
    }

    private fun UppaalTraceState.isOxstsBoundary(): Boolean {
        return locations.any { it.endsWith(".Start") || it.endsWith(".Running") }
    }

    private fun parseValue(variable: VariableDeclaration, raw: String): Expression? {
        val kind = runCatching { variableTransformer.describe(variable).kind }.getOrNull() ?: return null
        return when (kind) {
            UppaalVariableKind.Integer -> raw.toIntOrNull()?.let { OxstsFactory.createLiteralInteger(it) }
            UppaalVariableKind.Boolean -> when (raw) {
                "0" -> OxstsFactory.createLiteralBoolean(false)
                "1" -> OxstsFactory.createLiteralBoolean(true)
                else -> null
            }
            UppaalVariableKind.Enum -> {
                val enumDecl = variable.typeSpecification?.domain as? EnumDeclaration ?: return null
                // Uppaal encodes enum literals as their integer ordinal; map back via declaration order.
                val ordinal = raw.toIntOrNull() ?: return null
                val literal = enumDecl.literals.getOrNull(ordinal) ?: return null
                OxstsFactory.createElementReference(literal)
            }
            UppaalVariableKind.Clock -> null
        }
    }
}
