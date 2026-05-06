/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.nuxmv.transformation

import com.google.inject.Inject
import hu.bme.mit.semantifyr.compiler.pipeline.utils.eAllOfType
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration
import hu.bme.mit.semantifyr.utils.text.IndentingBuilder

data class NuxmvArtifacts(
    val smv: String,
    val property: NuxmvProperty,
)

class NuxmvModelTransformer @Inject constructor(
    private val nuxmvVariableTransformer: NuxmvVariableTransformer,
    private val nuxmvExpressionTransformer: NuxmvExpressionTransformer,
    private val nuxmvOperationTransformer: NuxmvOperationTransformer,
    private val nuxmvPropertyTransformer: NuxmvPropertyTransformer,
    private val nuxmvBranchSeeder: NuxmvBranchSeeder,
    private val nuxmvDeclarationRenderer: NuxmvDeclarationRenderer,
    private val nuxmvFormulaRenderer: NuxmvFormulaRenderer,
) {

    fun transform(inlinedOxsts: InlinedOxsts): NuxmvArtifacts {
        val variables = inlinedOxsts.eAllOfType<VariableDeclaration>().toList()
        val nuxmvVariables = variables.map {
            nuxmvVariableTransformer.describe(it)
        }
        val initializedVariables = collectInitializedVariables(variables)
        val initResults = transformInitBranches(inlinedOxsts, variables, initializedVariables)
        val tranResults = transformTranBranches(inlinedOxsts)
        val allResults = initResults + tranResults

        val builder = IndentingBuilder()
        builder.line("MODULE main")
        nuxmvDeclarationRenderer.renderVariablesSection(builder, nuxmvVariables, allPrimedDeclarations(allResults))
        nuxmvDeclarationRenderer.renderInputVariablesSection(builder, allInputVariables(allResults))
        nuxmvDeclarationRenderer.renderFrozenVariablesSection(builder, allFrozenVariables(allResults))
        nuxmvFormulaRenderer.renderInitSection(builder, variables, initResults.map { it.branch }, initializedVariables)
        nuxmvFormulaRenderer.renderTransSection(builder, variables, tranResults.map { it.branch })

        return NuxmvArtifacts(
            builder.toString(),
            nuxmvPropertyTransformer.transform(inlinedOxsts.property),
        )
    }

    private fun collectInitializedVariables(variables: List<VariableDeclaration>): Map<VariableDeclaration, String> {
        return variables.filter {
            it.expression != null
        }.associateWith {
            nuxmvExpressionTransformer.transform(it.expression)
        }
    }

    private fun transformInitBranches(
        inlinedOxsts: InlinedOxsts,
        variables: List<VariableDeclaration>,
        initializedVariables: Map<VariableDeclaration, String>,
    ): List<NuxmvBranchResult> {
        return inlinedOxsts.initTransition.branches.mapIndexed { branchIndex, branch ->
            val seed = nuxmvBranchSeeder.seedInitBranch(variables, initializedVariables)
            nuxmvOperationTransformer.transform(branch, NuxmvTransitionKind.Init, "b$branchIndex", seed)
        }
    }

    private fun transformTranBranches(inlinedOxsts: InlinedOxsts): List<NuxmvBranchResult> {
        return inlinedOxsts.mainTransition.branches.mapIndexed { branchIndex, branch ->
            nuxmvOperationTransformer.transform(branch, NuxmvTransitionKind.Tran, "b$branchIndex")
        }
    }

    private fun allPrimedDeclarations(results: List<NuxmvBranchResult>): List<NuxmvPrimedDeclaration> {
        return results.flatMap {
            it.branch.newPrimes
        }
    }

    private fun allInputVariables(results: List<NuxmvBranchResult>): List<NuxmvIVariable> {
        return results.flatMap {
            it.inputVariables
        }
    }

    private fun allFrozenVariables(results: List<NuxmvBranchResult>): List<NuxmvFrozenVariable> {
        return results.flatMap {
            it.frozenVariables
        }
    }
}
