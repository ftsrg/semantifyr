/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.nuxmv.trace

import com.google.inject.Inject
import hu.bme.mit.semantifyr.backend.scopes.VerificationScoped
import hu.bme.mit.semantifyr.backend.witness.InlinedOxstsAssumptionWitness
import hu.bme.mit.semantifyr.backend.witness.InlinedOxstsAssumptionWitnessState
import hu.bme.mit.semantifyr.backend.witness.InlinedOxstsAssumptionWitnessStateValue
import hu.bme.mit.semantifyr.backends.nuxmv.transformation.NuxmvVariableKind
import hu.bme.mit.semantifyr.backends.nuxmv.transformation.NuxmvVariableTransformer
import hu.bme.mit.semantifyr.compiler.pipeline.utils.OxstsFactory
import hu.bme.mit.semantifyr.compiler.pipeline.utils.copy
import hu.bme.mit.semantifyr.oxsts.model.oxsts.EnumDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration

@VerificationScoped
class NuxmvInlinedOxstsWitnessTransformer @Inject constructor(
    private val variableTransformer: NuxmvVariableTransformer,
) {

    fun transform(inlinedOxsts: InlinedOxsts, trace: NuxmvTrace): InlinedOxstsAssumptionWitness {
        val nameToVar = inlinedOxsts.variables.associateBy { variableTransformer.nameOf(it) }

        val running = mutableMapOf<VariableDeclaration, Expression>()
        val updated = mutableSetOf<VariableDeclaration>()

        val states = trace.states.map { state ->
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
                running[variable]?.let { InlinedOxstsAssumptionWitnessStateValue(variable, it.copy()) }
            }
            InlinedOxstsAssumptionWitnessState(values, activatedTraces = emptyList())
        }

        require(states.isNotEmpty()) { "Cannot build witness from an empty nuXmv trace" }

        val pre = InlinedOxstsAssumptionWitnessState(values = emptyList(), activatedTraces = emptyList())
        val initialized = states.first()
        val transitions = states.drop(1)

        val nextStateMap = mutableMapOf<InlinedOxstsAssumptionWitnessState, List<InlinedOxstsAssumptionWitnessState>>()
        if (initialized != null && transitions.isNotEmpty()) {
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
        val kind = variableKind(variable) ?: return null
        return when (kind) {
            NuxmvVariableKind.Integer -> raw.toIntOrNull()?.let { OxstsFactory.createLiteralInteger(it) }
            NuxmvVariableKind.Boolean -> when (raw.uppercase()) {
                "TRUE" -> OxstsFactory.createLiteralBoolean(true)
                "FALSE" -> OxstsFactory.createLiteralBoolean(false)
                else -> null
            }
            NuxmvVariableKind.Enum -> {
                val enumDecl = variable.typeSpecification?.domain as? EnumDeclaration ?: return null
                val literal = enumDecl.literals.firstOrNull { variableTransformer.sanitizeEnumLiteral(it) == raw }
                    ?: return null
                OxstsFactory.createElementReference(literal)
            }
        }
    }

    private fun variableKind(variable: VariableDeclaration): NuxmvVariableKind? {
        return runCatching { variableTransformer.describe(variable).kind }.getOrNull()
    }
}
