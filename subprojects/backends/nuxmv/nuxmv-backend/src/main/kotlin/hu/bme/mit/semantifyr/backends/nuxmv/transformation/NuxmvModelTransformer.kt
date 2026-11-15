/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.nuxmv.transformation

import com.google.inject.Inject
import hu.bme.mit.semantifyr.compiler.pipeline.utils.eAllOfType
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ForOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration
import hu.bme.mit.semantifyr.utils.text.IndentingStringBuilder

// no mangled name holds "_0", so the delay names no variable of the model
const val NUXMV_DELAY_NAME = "delay_0"

data class NuxmvArtifacts(
    val smv: String,
    val property: NuxmvProperty,
    val auxiliaryNames: Set<String>,
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
        val variables = inlinedOxsts.eAllOfType<VariableDeclaration>().filter {
            it.eContainer() !is ForOperation
        }.toList()
        val nuxmvVariables = variables.map {
            nuxmvVariableTransformer.describe(it)
        }
        val initializedVariables = collectInitializedVariables(variables)
        val clocks = variables.filter {
            nuxmvVariableTransformer.isClock(it)
        }
        val initResults = transformInitBranches(inlinedOxsts, variables, initializedVariables)
        val tranResults = transformTranBranches(inlinedOxsts, clocks)
        val allResults = initResults + tranResults

        val builder = IndentingStringBuilder()
        builder.appendLine("MODULE main")
        val primedDeclarations = allPrimedDeclarations(allResults)
        val frozenVariables = allFrozenVariables(allResults)
        nuxmvDeclarationRenderer.renderVariablesSection(builder, nuxmvVariables, primedDeclarations)
        nuxmvDeclarationRenderer.renderInputVariablesSection(builder, delayVariables(clocks) + allInputVariables(allResults))
        nuxmvDeclarationRenderer.renderFrozenVariablesSection(builder, frozenVariables)
        nuxmvFormulaRenderer.renderInitSection(builder, variables, initResults.map { it.branch }, initializedVariables)
        nuxmvFormulaRenderer.renderTransSection(builder, variables, tranResults.map { it.branch })

        return NuxmvArtifacts(
            builder.toString(),
            nuxmvPropertyTransformer.transform(inlinedOxsts.property),
            primedDeclarations.map {
                it.name
            }.toSet() + frozenVariables.map {
                it.name
            },
        )
    }

    private fun collectInitializedVariables(variables: List<VariableDeclaration>): Map<VariableDeclaration, String> {
        return variables.associateWith {
            val expression = it.expression ?: error("Variable ${it.name} has no initializer")
            nuxmvExpressionTransformer.transform(expression)
        }
    }

    private fun transformInitBranches(
        inlinedOxsts: InlinedOxsts,
        variables: List<VariableDeclaration>,
        initializedVariables: Map<VariableDeclaration, String>,
    ): List<NuxmvBranchResult> {
        return inlinedOxsts.initTransition.branches.mapIndexed { branchIndex, branch ->
            val branchTag = "b$branchIndex"
            val seed = nuxmvBranchSeeder.seedInitBranch(variables, initializedVariables, branchTag)
            nuxmvOperationTransformer.transform(branch, NuxmvTransitionKind.Init, branchTag, seed)
        }
    }

    private fun transformTranBranches(inlinedOxsts: InlinedOxsts, clocks: List<VariableDeclaration>): List<NuxmvBranchResult> {
        val seed = waitSeed(clocks)
        return inlinedOxsts.mainTransition.branches.mapIndexed { branchIndex, branch ->
            nuxmvOperationTransformer.transform(branch, NuxmvTransitionKind.Tran, "b$branchIndex", seed)
        }
    }

    // a main step waits before its transition, every clock advancing by the same amount, and init passes no time
    private fun waitSeed(clocks: List<VariableDeclaration>): NuxmvBranch {
        if (clocks.isEmpty()) {
            return NuxmvBranch()
        }
        val waited = clocks.associateWith {
            "(${nuxmvVariableTransformer.nameOf(it)} + $NUXMV_DELAY_NAME)"
        }
        return NuxmvBranch(constraints = listOf("$NUXMV_DELAY_NAME >= 0"), currentPrime = waited)
    }

    private fun delayVariables(clocks: List<VariableDeclaration>): List<NuxmvIVariable> {
        if (clocks.isEmpty()) {
            return emptyList()
        }
        return listOf(NuxmvIVariable(NUXMV_DELAY_NAME, "real"))
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
