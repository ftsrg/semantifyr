/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.spin.trace

import com.google.inject.Inject
import hu.bme.mit.semantifyr.backend.scopes.VerificationScoped
import hu.bme.mit.semantifyr.backend.witness.InlinedOxstsAssumptionWitness
import hu.bme.mit.semantifyr.backend.witness.InlinedOxstsAssumptionWitnessState
import hu.bme.mit.semantifyr.backend.witness.InlinedOxstsAssumptionWitnessStateValue
import hu.bme.mit.semantifyr.backends.spin.transformation.SpinPropertyTransformer
import hu.bme.mit.semantifyr.backends.spin.transformation.SpinVariableKind
import hu.bme.mit.semantifyr.backends.spin.transformation.SpinVariableTransformer
import hu.bme.mit.semantifyr.compiler.pipeline.utils.OxstsFactory
import hu.bme.mit.semantifyr.compiler.pipeline.utils.copy
import hu.bme.mit.semantifyr.oxsts.model.oxsts.EnumDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration

@VerificationScoped
class SpinInlinedOxstsWitnessTransformer @Inject constructor(
    private val variableTransformer: SpinVariableTransformer,
) {

    fun transform(inlinedOxsts: InlinedOxsts, trace: SpinTrace): InlinedOxstsAssumptionWitness {
        val nameToVar = inlinedOxsts.variables.associateBy { variableTransformer.nameOf(it) }

        val running = mutableMapOf<VariableDeclaration, Expression>()
        val updated = mutableSetOf<VariableDeclaration>()

        val states = trace.states.mapNotNull { state ->
            for ((name, raw) in state.variableValues) {
                val variable = nameToVar[name] ?: continue
                val value = parseValue(variable, raw) ?: continue
                running[variable] = value
                updated += variable
            }
            val stable = state.variableValues[SpinPropertyTransformer.STABLE_FLAG]
            if (stable != "1" && stable != "true") {
                return@mapNotNull null
            }
            val values = inlinedOxsts.variables.mapNotNull { variable ->
                if (variable !in updated) {
                    return@mapNotNull null
                }
                running[variable]?.let { InlinedOxstsAssumptionWitnessStateValue(variable, it.copy()) }
            }
            InlinedOxstsAssumptionWitnessState(values, activatedTraces = emptyList())
        }

        require(states.isNotEmpty()) { "Cannot build witness from an empty Spin trace" }

        val pre = InlinedOxstsAssumptionWitnessState(values = emptyList(), activatedTraces = emptyList())
        val initialized = states.first()
        val transitions = states.drop(1)

        val nextStateMap = mutableMapOf<InlinedOxstsAssumptionWitnessState, List<InlinedOxstsAssumptionWitnessState>>()
        if (transitions.isNotEmpty()) {
            nextStateMap[initialized] = listOf(transitions.first())
        }
        for (i in 1 until transitions.size) {
            nextStateMap[transitions[i - 1]] = listOf(transitions[i])
        }

        return InlinedOxstsAssumptionWitness(
            initialState = pre,
            initializedState = initialized,
            transitionStates = transitions,
            nextStateMap = nextStateMap,
            inlinedOxsts = inlinedOxsts,
        )
    }

    private fun parseValue(variable: VariableDeclaration, raw: String): Expression? {
        val kind = runCatching { variableTransformer.describe(variable).kind }.getOrNull() ?: return null
        return when (kind) {
            SpinVariableKind.Integer -> raw.toIntOrNull()?.let { OxstsFactory.createLiteralInteger(it) }
            SpinVariableKind.Boolean -> when (raw) {
                "0" -> OxstsFactory.createLiteralBoolean(false)
                "1" -> OxstsFactory.createLiteralBoolean(true)
                "false" -> OxstsFactory.createLiteralBoolean(false)
                "true" -> OxstsFactory.createLiteralBoolean(true)
                else -> null
            }
            SpinVariableKind.Enum -> {
                val enumDecl = variable.typeSpecification?.domain as? EnumDeclaration ?: return null
                val literal = enumDecl.literals.firstOrNull { it.name == raw }
                    ?: raw.toIntOrNull()?.let { enumDecl.literals.getOrNull(it) }
                    ?: return null
                OxstsFactory.createElementReference(literal)
            }
        }
    }
}
