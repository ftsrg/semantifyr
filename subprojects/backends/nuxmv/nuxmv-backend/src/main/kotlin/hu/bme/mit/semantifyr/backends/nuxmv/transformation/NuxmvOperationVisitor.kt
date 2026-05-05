/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.nuxmv.transformation

import com.google.inject.assistedinject.Assisted
import com.google.inject.assistedinject.AssistedInject
import hu.bme.mit.semantifyr.backend.BackendUnsupportedException
import hu.bme.mit.semantifyr.backend.transformation.BackendOperationVisitor
import hu.bme.mit.semantifyr.oxsts.lang.utils.OxstsUtils
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AssignmentOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AssumptionOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ChoiceOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ForOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.HavocOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.IfOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LocalVarDeclarationOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Operation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.SequenceOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration

class NuxmvOperationVisitor @AssistedInject constructor(
    @param:Assisted private val transitionKind: NuxmvTransitionKind,
    @param:Assisted private val branchTag: String,
    @Assisted seed: NuxmvBranch,
    private val nuxmvExpressionTransformer: NuxmvExpressionTransformer,
    private val nuxmvVariableTransformer: NuxmvVariableTransformer,
) : BackendOperationVisitor<NuxmvBranch>() {

    private var nextChoiceId = 0
    private val primeCounter = mutableMapOf<VariableDeclaration, Int>()
    private val collectedInputVariables = mutableListOf<NuxmvIVariable>()
    private val collectedFrozenVariables = mutableListOf<NuxmvFrozenVariable>()
    private var accumulated = seed

    init {
        for (declaration in seed.newPrimes) {
            primeCounter[declaration.variable] = (primeCounter[declaration.variable] ?: 0) + 1
        }
    }

    fun transform(operation: Operation): NuxmvBranchResult {
        val branch = visit(operation)
        return NuxmvBranchResult(
            branch,
            collectedInputVariables.toList(),
            collectedFrozenVariables.toList(),
        )
    }

    override fun visit(operation: SequenceOperation): NuxmvBranch {
        for (step in operation.steps) {
            accumulated = visit(step)
        }
        return accumulated
    }

    override fun visit(operation: AssignmentOperation): NuxmvBranch {
        val variable = OxstsUtils.requireVariableReference(operation.reference)
        val rightHandSide = nuxmvExpressionTransformer.transform(operation.expression, accumulated.currentPrime)
        val primed = freshPrime(variable)
        return accumulated.copy(
            constraints = accumulated.constraints + "${primed.name} = $rightHandSide",
            currentPrime = accumulated.currentPrime + (variable to primed.name),
            newPrimes = accumulated.newPrimes + primed,
        )
    }

    override fun visit(operation: AssumptionOperation): NuxmvBranch {
        val expression = nuxmvExpressionTransformer.transform(operation.expression, accumulated.currentPrime)
        return accumulated.copy(constraints = accumulated.constraints + expression)
    }

    override fun visit(operation: LocalVarDeclarationOperation): NuxmvBranch {
        val initExpression = operation.expression
        if (initExpression == null) {
            val primed = freshPrime(operation)
            return accumulated.copy(
                currentPrime = accumulated.currentPrime + (operation to primed.name),
                newPrimes = accumulated.newPrimes + primed,
            )
        }
        val rightHandSide = nuxmvExpressionTransformer.transform(initExpression, accumulated.currentPrime)
        val primed = freshPrime(operation)
        return accumulated.copy(
            constraints = accumulated.constraints + "${primed.name} = $rightHandSide",
            currentPrime = accumulated.currentPrime + (operation to primed.name),
            newPrimes = accumulated.newPrimes + primed,
        )
    }

    override fun visit(operation: HavocOperation): NuxmvBranch {
        val variable = OxstsUtils.requireVariableReference(operation.reference)
        val primed = freshPrime(variable)
        return accumulated.copy(
            currentPrime = accumulated.currentPrime + (variable to primed.name),
            newPrimes = accumulated.newPrimes + primed,
        )
    }

    override fun visit(operation: ChoiceOperation): NuxmvBranch {
        val branches = operation.branches
        if (branches.isEmpty()) {
            return accumulated.copy(constraints = accumulated.constraints + "FALSE")
        }
        if (branches.size == 1) {
            return visit(branches.single())
        }

        val base = accumulated
        val childBranches = branches.map {
            accumulated = base
            visit(it)
        }
        accumulated = base

        val keyExpressions = allocateChoiceKeys(branches.size)
        return commonize(base, childBranches, keyExpressions)
    }

    override fun visit(operation: IfOperation): NuxmvBranch {
        val guardString = nuxmvExpressionTransformer.transform(operation.guard, accumulated.currentPrime)
        val base = accumulated

        accumulated = base
        val thenBranch = visit(operation.body)

        accumulated = base
        val elseBranch = operation.`else`?.let { visit(it) } ?: base

        accumulated = base
        val keyExpressions = listOf(guardString, "TRUE")
        return commonize(base, listOf(thenBranch, elseBranch), keyExpressions)
    }

    override fun visit(operation: ForOperation): NuxmvBranch {
        throw BackendUnsupportedException("nuXmv does not support for-operations")
    }

    private fun freshPrime(variable: VariableDeclaration): NuxmvPrimedDeclaration {
        val primeIndex = (primeCounter[variable] ?: 0) + 1
        primeCounter[variable] = primeIndex
        val base = nuxmvVariableTransformer.nameOf(variable)
        return NuxmvPrimedDeclaration("${base}__${transitionKind.tagName}_${branchTag}_$primeIndex", variable)
    }

    private fun allocateChoiceKeys(branchCount: Int): List<String> {
        val name = "nondet_${transitionKind.tagName}_${nextChoiceId++}"
        val keyName = when (transitionKind) {
            NuxmvTransitionKind.Init -> freshFrozenVariable(name, branchCount).name
            NuxmvTransitionKind.Tran -> freshInputVariable(name, branchCount).name
        }
        return (0 until branchCount).map { "$keyName = $it" }
    }

    private fun freshInputVariable(name: String, branchCount: Int): NuxmvIVariable {
        require(branchCount >= 2) { "input variable branch count must be >= 2 (got $branchCount)" }
        val inputVariable = NuxmvIVariable(name, "0..${branchCount - 1}")
        collectedInputVariables += inputVariable
        return inputVariable
    }

    private fun freshFrozenVariable(name: String, branchCount: Int): NuxmvFrozenVariable {
        require(branchCount >= 2) { "frozen variable branch count must be >= 2 (got $branchCount)" }
        val frozenVariable = NuxmvFrozenVariable(name, "0..${branchCount - 1}")
        collectedFrozenVariables += frozenVariable
        return frozenVariable
    }

    private fun commonize(
        base: NuxmvBranch,
        childBranches: List<NuxmvBranch>,
        keyExpressions: List<String>,
    ): NuxmvBranch {
        require(childBranches.size == keyExpressions.size) {
            "commonize: branches (${childBranches.size}) and keys (${keyExpressions.size}) must match"
        }

        val mergedConstraints = base.constraints.toMutableList()
        val mergedPrimes = base.newPrimes.toMutableList()
        val mergedCurrent = base.currentPrime.toMutableMap()

        appendBranchExtras(mergedConstraints, base, childBranches, keyExpressions)
        appendChildPrimes(mergedPrimes, base, childBranches)

        for (variable in collectTouchedVariables(base, childBranches)) {
            val merged = freshPrime(variable)
            mergedPrimes += merged
            mergedConstraints += renderMergeConstraint(merged, variable, base, childBranches, keyExpressions)
            mergedCurrent[variable] = merged.name
        }

        return NuxmvBranch(mergedConstraints, mergedCurrent, mergedPrimes)
    }

    private fun collectTouchedVariables(
        base: NuxmvBranch,
        childBranches: List<NuxmvBranch>,
    ): Set<VariableDeclaration> {
        return childBranches.flatMap {
            it.currentPrime.entries
        }.filter { (variable, name) ->
            base.currentPrime[variable] != name
        }.map {
            it.key
        }.toSet()
    }

    private fun appendBranchExtras(
        mergedConstraints: MutableList<String>,
        base: NuxmvBranch,
        childBranches: List<NuxmvBranch>,
        keyExpressions: List<String>,
    ) {
        val branchExtras = childBranches.map {
            check(it.constraints.size >= base.constraints.size) {
                "child branch shrank constraints (was ${base.constraints.size}, became ${it.constraints.size})"
            }
            it.constraints.subList(base.constraints.size, it.constraints.size)
        }
        if (branchExtras.all { it.isEmpty() }) {
            return
        }
        val caseArms = keyExpressions.zip(branchExtras).map { (key, extraConstraints) ->
            val body = if (extraConstraints.isEmpty()) {
                "TRUE"
            } else {
                extraConstraints.joinToString(" & ") { "($it)" }
            }
            "$key : $body;"
        }
        mergedConstraints += renderCase(caseArms)
    }

    private fun appendChildPrimes(
        mergedPrimes: MutableList<NuxmvPrimedDeclaration>,
        base: NuxmvBranch,
        childBranches: List<NuxmvBranch>,
    ) {
        for (child in childBranches) {
            check(child.newPrimes.size >= base.newPrimes.size) {
                "child branch shrank newPrimes (was ${base.newPrimes.size}, became ${child.newPrimes.size})"
            }
            mergedPrimes += child.newPrimes.subList(base.newPrimes.size, child.newPrimes.size)
        }
    }

    private fun renderMergeConstraint(
        merged: NuxmvPrimedDeclaration,
        variable: VariableDeclaration,
        base: NuxmvBranch,
        childBranches: List<NuxmvBranch>,
        keyExpressions: List<String>,
    ): String {
        val baselineName = base.currentPrime[variable] ?: nuxmvVariableTransformer.nameOf(variable)
        val perChild = childBranches.map {
            it.currentPrime[variable] ?: baselineName
        }
        val caseArms = keyExpressions.zip(perChild).map { (key, expression) ->
            "$key : $expression;"
        }
        return "${merged.name} = ${renderCase(caseArms)}"
    }

    private fun renderCase(caseArms: List<String>): String {
        return "case ${caseArms.joinToString(" ")} esac"
    }

    interface Factory {
        fun create(
            transitionKind: NuxmvTransitionKind,
            branchTag: String,
            seed: NuxmvBranch,
        ): NuxmvOperationVisitor
    }
}
