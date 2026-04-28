/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.uppaal.trace

import com.google.inject.Inject
import hu.bme.mit.semantifyr.backend.scopes.VerificationScoped
import hu.bme.mit.semantifyr.backend.witness.InlinedOxstsAssumptionWitness
import hu.bme.mit.semantifyr.backend.witness.InlinedOxstsAssumptionWitnessState
import hu.bme.mit.semantifyr.backend.witness.InlinedOxstsAssumptionWitnessStateValue
import hu.bme.mit.semantifyr.backends.uppaal.transformation.UppaalVariableKind
import hu.bme.mit.semantifyr.backends.uppaal.transformation.UppaalVariableTransformer
import hu.bme.mit.semantifyr.compiler.pipeline.utils.OxstsFactory
import hu.bme.mit.semantifyr.oxsts.model.oxsts.EnumDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration

@VerificationScoped
class UppaalInlinedOxstsWitnessTransformer @Inject constructor(
    private val variableTransformer: UppaalVariableTransformer,
) {

    fun transform(inlinedOxsts: InlinedOxsts, trace: UppaalTrace): InlinedOxstsAssumptionWitness {
        val nameToVar = inlinedOxsts.variables.associateBy { variableTransformer.nameOf(it) }

        val boundaryStates = trace.states.filter { it.isOxstsBoundary() }

        val states = boundaryStates.map { state ->
            val values = state.variableValues.mapNotNull { (name, raw) ->
                val variable = nameToVar[name] ?: return@mapNotNull null
                val value = parseValue(variable, raw) ?: return@mapNotNull null
                InlinedOxstsAssumptionWitnessStateValue(variable, value)
            }
            InlinedOxstsAssumptionWitnessState(values, activatedTraces = emptyList())
        }

        require(states.isNotEmpty()) { "Cannot build witness from an empty Uppaal trace" }

        val initial = states[0]
        val initialized = states.getOrNull(1)
        val transitions = if (initialized != null) states.drop(2) else emptyList()

        val nextStateMap = mutableMapOf<InlinedOxstsAssumptionWitnessState, List<InlinedOxstsAssumptionWitnessState>>()
        if (initialized != null && transitions.isNotEmpty()) {
            nextStateMap[initialized] = listOf(transitions.first())
        }
        for (i in 1 until transitions.size) {
            nextStateMap[transitions[i - 1]] = listOf(transitions[i])
        }

        return InlinedOxstsAssumptionWitness(
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
