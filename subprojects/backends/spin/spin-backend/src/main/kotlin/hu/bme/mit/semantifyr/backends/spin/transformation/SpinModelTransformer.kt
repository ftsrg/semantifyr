/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.spin.transformation

import com.google.inject.Inject
import hu.bme.mit.semantifyr.backend.scopes.VerificationScoped
import hu.bme.mit.semantifyr.oxsts.model.oxsts.EnumDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts

data class SpinArtifacts(
    val promela: String,
    val property: SpinProperty,
)

@VerificationScoped
class SpinModelTransformer @Inject constructor(
    private val spinVariableTransformer: SpinVariableTransformer,
    private val spinOperationTransformer: SpinOperationTransformer,
    private val spinPropertyTransformer: SpinPropertyTransformer,
) {

    fun transform(inlinedOxsts: InlinedOxsts): SpinArtifacts {
        val globals = inlinedOxsts.variables.map {
            spinVariableTransformer.describe(it)
        }
        val enums = globals
            .asSequence()
            .mapNotNull { it.enumDeclaration }
            .distinct()
            .toList()

        val builder = StringBuilder()
        renderEnums(builder, enums)
        renderGlobals(builder, globals)
        renderStableFlag(builder)
        renderInitProctype(builder, inlinedOxsts)

        val property = spinPropertyTransformer.transform(inlinedOxsts.property)
        builder.append('\n')
        builder.append("ltl p { ").append(property.ltl).append(" }\n")

        return SpinArtifacts(promela = builder.toString(), property = property)
    }

    private fun renderStableFlag(builder: StringBuilder) {
        builder.append("bool ").append(SpinPropertyTransformer.STABLE_FLAG).append(" = false;\n\n")
    }

    private fun renderEnums(
        builder: StringBuilder,
        enums: List<EnumDeclaration>,
    ) {
        for (enum in enums) {
            builder.append("mtype:").append(enum.name).append(" = { ")
            builder.append(
                enum.literals.joinToString(", ") {
                    spinVariableTransformer.sanitizeEnumLiteral(it)
                },
            )
            builder.append(" };\n")
        }
        if (enums.isNotEmpty()) builder.append('\n')
    }

    private fun renderGlobals(
        builder: StringBuilder,
        globals: List<SpinVariable>,
    ) {
        for (variable in globals) {
            val type = when (variable.kind) {
                SpinVariableKind.Integer -> "int"
                SpinVariableKind.Boolean -> "bool"
                SpinVariableKind.Enum -> "mtype:${variable.enumDeclaration!!.name}"
            }
            builder.append(type).append(' ').append(variable.name)
            variable.initialValue?.let { builder.append(" = ").append(it) }
            builder.append(";\n")
        }
        if (globals.isNotEmpty()) builder.append('\n')
    }

    private fun renderInitProctype(
        builder: StringBuilder,
        inlinedOxsts: InlinedOxsts,
    ) {
        builder.append("init {\n")

        val stableFlag = SpinPropertyTransformer.STABLE_FLAG

        val initBranches = inlinedOxsts.initTransition.branches
        if (initBranches.size == 1) {
            val body = spinOperationTransformer.transform(initBranches.single(), indent = "        ")
            builder.append("    atomic {\n")
            builder.append("        $stableFlag = false;\n")
            if (body.isNotBlank()) {
                builder.append(body)
            }
            builder.append("        $stableFlag = true;\n")
            builder.append("    }\n")
        } else if (initBranches.size > 1) {
            builder.append("    if\n")
            for (branch in initBranches) {
                val body = spinOperationTransformer.transform(branch, indent = "        ")
                builder.append("    :: atomic {\n")
                builder.append("        $stableFlag = false;\n")
                if (body.isBlank()) {
                    builder.append("        skip;\n")
                } else {
                    builder.append(body)
                }
                builder.append("        $stableFlag = true;\n")
                builder.append("    }\n")
            }
            builder.append("    fi;\n")
        } else {
            // No init at all - we still need the stable flag flipped to true so the LTL gate
            // permits property evaluation at the very first state of the main loop.
            builder.append("    atomic { $stableFlag = true; }\n")
        }

        val tranBranches = inlinedOxsts.mainTransition.branches
        val tranBodies = tranBranches.map { spinOperationTransformer.transform(it, indent = "        ") }
        if (tranBodies.any { it.isNotBlank() }) {
            builder.append("    do\n")
            for ((branch, body) in tranBranches.zip(tranBodies)) {
                builder.append("    :: atomic {\n")
                builder.append("        $stableFlag = false;\n")
                if (body.isBlank()) {
                    builder.append("        skip;\n")
                } else {
                    builder.append(body)
                }
                builder.append("        $stableFlag = true;\n")
                builder.append("    }\n")
            }
            builder.append("    od;\n")
        }

        builder.append("}\n")
    }
}
