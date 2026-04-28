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
import hu.bme.mit.semantifyr.backends.spin.transformation.SpinVariableKind
import hu.bme.mit.semantifyr.backends.spin.transformation.SpinVariableTransformer
import hu.bme.mit.semantifyr.compiler.pipeline.utils.OxstsFactory
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AssignmentOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.EnumDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.HavocOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.TransitionDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration

@VerificationScoped
class SpinInlinedOxstsWitnessTransformer @Inject constructor(
    private val variableTransformer: SpinVariableTransformer,
) {

    fun transform(inlinedOxsts: InlinedOxsts, trace: SpinTrace): InlinedOxstsAssumptionWitness {
        val nameToVar = inlinedOxsts.variables.associateBy { variableTransformer.nameOf(it) }

        val states = trace.states.map { state ->
            val values = state.variableValues.mapNotNull { (name, raw) ->
                val variable = nameToVar[name] ?: return@mapNotNull null
                val value = parseValue(variable, raw) ?: return@mapNotNull null
                InlinedOxstsAssumptionWitnessStateValue(variable, value)
            }
            InlinedOxstsAssumptionWitnessState(values, activatedTraces = emptyList())
        }

        require(states.isNotEmpty()) { "Cannot build witness from an empty Spin trace" }

        val initHasEffect = inlinedOxsts.initTransition.hasObservableEffect()
        val pre = InlinedOxstsAssumptionWitnessState(values = emptyList(), activatedTraces = emptyList())
        val initialized = if (initHasEffect) states.first() else null
        val transitions = if (initHasEffect) states.drop(1) else states

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

    private fun TransitionDeclaration.hasObservableEffect(): Boolean {
        val iterator = this.eAllContents()
        while (iterator.hasNext()) {
            val obj = iterator.next()
            if (obj is AssignmentOperation || obj is HavocOperation) {
                return true
            }
        }
        return false
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
                val ordinal = raw.toIntOrNull() ?: return null
                val literal = enumDecl.literals.getOrNull(ordinal) ?: return null
                OxstsFactory.createElementReference(literal)
            }
        }
    }
}
