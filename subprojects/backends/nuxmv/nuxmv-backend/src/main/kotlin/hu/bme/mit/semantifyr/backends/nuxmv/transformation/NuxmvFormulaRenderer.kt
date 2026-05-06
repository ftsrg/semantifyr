/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.nuxmv.transformation

import com.google.inject.Inject
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration
import hu.bme.mit.semantifyr.utils.text.IndentingStringBuilder

class NuxmvFormulaRenderer @Inject constructor(
    private val nuxmvVariableTransformer: NuxmvVariableTransformer,
) {

    fun renderInitSection(
        builder: IndentingStringBuilder,
        allVariables: List<VariableDeclaration>,
        branches: List<NuxmvBranch>,
        declaredInitial: Map<VariableDeclaration, String>,
    ) {
        builder.appendLine("INIT")
        builder.indented {
            renderDisjunction(this, branches.ifEmpty { listOf(NuxmvBranch()) }) {
                it.constraints + finalizeInitBindings(allVariables, it, declaredInitial)
            }
        }
    }

    fun renderTransSection(
        builder: IndentingStringBuilder,
        allVariables: List<VariableDeclaration>,
        branches: List<NuxmvBranch>,
    ) {
        builder.appendLine("TRANS")
        builder.indented {
            renderDisjunction(this, branches.ifEmpty { listOf(NuxmvBranch()) }) {
                it.constraints + finalizeTransBindings(allVariables, it)
            }
        }
    }

    private fun finalizeInitBindings(
        allVariables: List<VariableDeclaration>,
        branch: NuxmvBranch,
        declaredInitial: Map<VariableDeclaration, String>,
    ): List<String> {
        val parts = mutableListOf<String>()
        for (variable in allVariables) {
            val name = nuxmvVariableTransformer.nameOf(variable)
            val rightHandSide = branch.currentPrime[variable]
            if (rightHandSide != null && rightHandSide != name) {
                parts += "$name = $rightHandSide"
            } else {
                declaredInitial[variable]?.let {
                    parts += "$name = $it"
                }
            }
        }
        return parts
    }

    private fun finalizeTransBindings(
        allVariables: List<VariableDeclaration>,
        branch: NuxmvBranch,
    ): List<String> {
        return allVariables.map {
            val name = nuxmvVariableTransformer.nameOf(it)
            val rightHandSide = branch.currentPrime[it] ?: name
            "next($name) = $rightHandSide"
        }
    }

    private fun renderDisjunction(
        builder: IndentingStringBuilder,
        branches: List<NuxmvBranch>,
        partsOf: (NuxmvBranch) -> List<String>,
    ) {
        for ((index, branch) in branches.withIndex()) {
            val opener = if (index == 0) {
                "("
            } else {
                "| ("
            }
            builder.appendLine(opener)
            builder.indented {
                renderConjunction(this, partsOf(branch))
            }
            builder.appendLine(")")
        }
    }

    private fun renderConjunction(builder: IndentingStringBuilder, parts: List<String>) {
        val nonEmpty = parts.filter {
            it.isNotEmpty()
        }
        if (nonEmpty.isEmpty()) {
            builder.appendLine("TRUE")
            return
        }
        for ((index, part) in nonEmpty.withIndex()) {
            val prefix = if (index == 0) {
                "  "
            } else {
                "& "
            }
            builder.appendLine("$prefix($part)")
        }
    }
}
