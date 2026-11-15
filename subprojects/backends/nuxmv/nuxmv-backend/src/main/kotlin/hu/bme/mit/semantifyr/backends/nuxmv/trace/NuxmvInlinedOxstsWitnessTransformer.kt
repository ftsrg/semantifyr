/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.nuxmv.trace

import com.google.inject.Inject
import hu.bme.mit.semantifyr.backend.witness.InlinedWitness
import hu.bme.mit.semantifyr.backend.witness.WitnessFailedException
import hu.bme.mit.semantifyr.backend.witness.WitnessState
import hu.bme.mit.semantifyr.backend.witness.WitnessStateValue
import hu.bme.mit.semantifyr.backend.witness.linearInlinedWitness
import hu.bme.mit.semantifyr.backends.nuxmv.transformation.NuxmvVariableKind
import hu.bme.mit.semantifyr.backends.nuxmv.transformation.NuxmvVariableTransformer
import hu.bme.mit.semantifyr.compiler.pipeline.utils.OxstsFactory
import hu.bme.mit.semantifyr.compiler.pipeline.utils.copy
import hu.bme.mit.semantifyr.compiler.pipeline.utils.eAllOfType
import hu.bme.mit.semantifyr.oxsts.model.oxsts.EnumDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration

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

        val running = mutableMapOf<VariableDeclaration, Expression>()
        val updated = mutableSetOf<VariableDeclaration>()

        val states = trace.states.map {
            for ((name, raw) in it.variableValues) {
                if (name in auxiliaryNames) {
                    continue
                }
                val variable = nameToVariable[name] ?: throw WitnessFailedException("nuXmv's trace holds $name, which names no variable of the model")
                if (variable !in stateVariables) {
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
            WitnessState(values)
        }

        return linearInlinedWitness(inlinedOxsts, states)
    }

    private fun parseValue(variable: VariableDeclaration, raw: String): Expression? {
        val kind = nuxmvVariableTransformer.describe(variable).kind
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
        }
    }
}
