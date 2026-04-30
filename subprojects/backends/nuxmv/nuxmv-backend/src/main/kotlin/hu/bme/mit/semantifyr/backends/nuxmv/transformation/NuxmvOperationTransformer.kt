/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.nuxmv.transformation

import com.google.inject.Inject
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AssignmentOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AssumptionOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ChoiceOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ElementReference
import hu.bme.mit.semantifyr.oxsts.model.oxsts.HavocOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.IfOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LocalVarDeclarationOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Operation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.SequenceOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration

class NuxmvOperationTransformer {
    @Inject
    private lateinit var nuxmvExpressionTransformer: NuxmvExpressionTransformer

    @Inject
    private lateinit var nuxmvVariableTransformer: NuxmvVariableTransformer

    private var nextIvarId: Int = 0
    private var nextFrozenVarId: Int = 0
    private var contextTag: String = ""
    private var branchTag: String = ""
    private val primeCounter: MutableMap<VariableDeclaration, Int> = mutableMapOf()

    fun transform(
        operation: Operation,
        contextTag: String,
        branchTag: String,
    ): NuxmvBranch {
        return transform(operation, contextTag, branchTag, NuxmvBranch())
    }

    fun transform(
        operation: Operation,
        contextTag: String,
        branchTag: String,
        seed: NuxmvBranch,
    ): NuxmvBranch {
        this.contextTag = contextTag
        this.branchTag = branchTag
        primeCounter.clear()
        // Seed primes already consumed bump counters so fresh names don't collide.
        for (decl in seed.newPrimes) {
            primeCounter[decl.variable] = (primeCounter[decl.variable] ?: 0) + 1
        }
        return fold(seed, operation)
    }

    fun freshPrime(variable: VariableDeclaration): NuxmvPrimedDecl {
        val n = (primeCounter[variable] ?: 0) + 1
        primeCounter[variable] = n
        val base = nuxmvVariableTransformer.nameOf(variable)
        return NuxmvPrimedDecl(name = "${base}__${contextTag}_${branchTag}_$n", variable = variable)
    }

    private fun fold(
        accumulated: NuxmvBranch,
        operation: Operation,
    ): NuxmvBranch {
        return when (operation) {
            is SequenceOperation -> foldSequence(accumulated, operation)
            is ChoiceOperation -> foldChoice(accumulated, operation)
            is AssignmentOperation -> foldAssignment(accumulated, operation)
            is AssumptionOperation -> foldAssumption(accumulated, operation)
            is IfOperation -> foldIf(accumulated, operation)
            is LocalVarDeclarationOperation -> foldLocalVarDecl(accumulated, operation)
            is HavocOperation -> foldHavoc(accumulated, operation)
            else -> error("Operation of kind ${operation::class.simpleName} is not supported by the nuXmv backend")
        }
    }

    private fun foldSequence(
        accumulated: NuxmvBranch,
        operation: SequenceOperation,
    ): NuxmvBranch {
        return operation.steps.fold(accumulated) { current, step -> fold(current, step) }
    }

    private fun foldAssignment(
        accumulated: NuxmvBranch,
        operation: AssignmentOperation,
    ): NuxmvBranch {
        val variable = resolveVariable(operation.reference)
        val rhs = nuxmvExpressionTransformer.transform(operation.expression, accumulated.currentPrime)
        val primed = freshPrime(variable)
        return accumulated.copy(
            constraints = accumulated.constraints + "${primed.name} = $rhs",
            currentPrime = accumulated.currentPrime + (variable to primed.name),
            newPrimes = accumulated.newPrimes + primed,
        )
    }

    private fun foldAssumption(
        accumulated: NuxmvBranch,
        operation: AssumptionOperation,
    ): NuxmvBranch {
        val expr = nuxmvExpressionTransformer.transform(operation.expression, accumulated.currentPrime)
        return accumulated.copy(constraints = accumulated.constraints + expr)
    }

    private fun foldLocalVarDecl(
        accumulated: NuxmvBranch,
        operation: LocalVarDeclarationOperation,
    ): NuxmvBranch {
        val initExpr = operation.expression
        if (initExpr == null) {
            // Local declared without initializer: pre-seed a free primed name so subsequent reads
            // resolve to it instead of the original SMV variable name.
            val primed = freshPrime(operation)
            return accumulated.copy(
                currentPrime = accumulated.currentPrime + (operation to primed.name),
                newPrimes = accumulated.newPrimes + primed,
            )
        }
        val rhs = nuxmvExpressionTransformer.transform(initExpr, accumulated.currentPrime)
        val primed = freshPrime(operation)
        return accumulated.copy(
            constraints = accumulated.constraints + "${primed.name} = $rhs",
            currentPrime = accumulated.currentPrime + (operation to primed.name),
            newPrimes = accumulated.newPrimes + primed,
        )
    }

    private fun foldHavoc(
        accumulated: NuxmvBranch,
        operation: HavocOperation,
    ): NuxmvBranch {
        val variable = resolveVariable(operation.reference)
        val primed = freshPrime(variable)
        return accumulated.copy(
            // No constraint - the prime is a fresh free SMV state var for the havoc'd value.
            currentPrime = accumulated.currentPrime + (variable to primed.name),
            newPrimes = accumulated.newPrimes + primed,
        )
    }

    private fun foldChoice(
        accumulated: NuxmvBranch,
        operation: ChoiceOperation,
    ): NuxmvBranch {
        val branches = operation.branches
        if (branches.isEmpty()) {
            return accumulated.copy(constraints = accumulated.constraints + "FALSE")
        }
        if (branches.size == 1) {
            return fold(accumulated, branches.single())
        }

        val childBranches = branches.map { child -> fold(accumulated, child) }
        if (contextTag == "init") {
            val frozen = choiceFrozenVar(name = "nondet_init_${nextFrozenVarId++}", branchCount = branches.size)
            return commonize(
                base = accumulated,
                childBranches = childBranches,
                extraIvars = emptyList(),
                extraFrozenVars = listOf(frozen),
                keyExprs = childBranches.indices.map { i -> "${frozen.name} = $i" },
            )
        }
        val ivar = choiceIVar(name = "nondet_${nextIvarId++}", branchCount = branches.size)
        return commonize(
            base = accumulated,
            childBranches = childBranches,
            extraIvars = listOf(ivar),
            extraFrozenVars = emptyList(),
            keyExprs = childBranches.indices.map { i -> "${ivar.name} = $i" },
        )
    }

    private fun foldIf(
        accumulated: NuxmvBranch,
        operation: IfOperation,
    ): NuxmvBranch {
        val guardStr = nuxmvExpressionTransformer.transform(operation.guard, accumulated.currentPrime)
        val thenBranch = fold(accumulated, operation.body)
        val elseBranch = operation.`else`?.let { fold(accumulated, it) } ?: accumulated
        return commonize(
            base = accumulated,
            childBranches = listOf(thenBranch, elseBranch),
            extraIvars = emptyList(),
            extraFrozenVars = emptyList(),
            keyExprs = listOf(guardStr, "TRUE"),
        )
    }

    /**
     * Merge per-branch fold results into a single [NuxmvBranch]: for each variable whose latest
     * prime differs across branches, allocate a fresh post-merge prime and bind it via a `case`
     * expression keyed on [keyExprs]. Per-branch extra constraints are case-wrapped under the
     * same keys so only the chosen branch's constraints fire.
     */
    private fun commonize(
        base: NuxmvBranch,
        childBranches: List<NuxmvBranch>,
        extraIvars: List<NuxmvIVar>,
        extraFrozenVars: List<NuxmvFrozenVar>,
        keyExprs: List<String>,
    ): NuxmvBranch {
        require(childBranches.size == keyExprs.size) {
            "commonize: branches (${childBranches.size}) and keys (${keyExprs.size}) must match"
        }

        val touchedVars: Set<VariableDeclaration> = childBranches
            .flatMap { it.currentPrime.entries }
            .filter { (v, name) -> base.currentPrime[v] != name }
            .map { it.key }
            .toSet()

        val mergedConstraints = base.constraints.toMutableList()
        val mergedPrimes = base.newPrimes.toMutableList()
        val mergedCurrent = base.currentPrime.toMutableMap()
        val mergedIvars = base.ivars + extraIvars
        val mergedFrozenVars = base.frozenVars + extraFrozenVars

        // Per-branch extra constraints: case-wrap on the key so they only fire in the matching branch.
        val branchExtras: List<List<String>> = childBranches.map { child ->
            child.constraints.subList(base.constraints.size, child.constraints.size)
        }
        if (branchExtras.any { it.isNotEmpty() }) {
            val arms = keyExprs.zip(branchExtras).map { (key, extras) ->
                val body = if (extras.isEmpty()) "TRUE" else extras.joinToString(" & ") { "($it)" }
                "$key : $body;"
            }
            mergedConstraints += "case ${arms.joinToString(" ")} esac"
        }

        // Each child's introduced primes (beyond base) need to be declared too.
        for (child in childBranches) {
            mergedPrimes += child.newPrimes.subList(base.newPrimes.size, child.newPrimes.size)
        }

        // Carry-over IVARs and FROZENVARs introduced inside child branches (e.g. nested choices).
        val childExtraIvars = childBranches.flatMap { child ->
            child.ivars.subList(base.ivars.size, child.ivars.size)
        }
        val childExtraFrozenVars = childBranches.flatMap { child ->
            child.frozenVars.subList(base.frozenVars.size, child.frozenVars.size)
        }

        // For each touched variable: introduce a post-merge prime bound by a case on the key.
        for (variable in touchedVars) {
            val baselineName = base.currentPrime[variable] ?: nuxmvVariableTransformer.nameOf(variable)
            val perChild = childBranches.map { child -> child.currentPrime[variable] ?: baselineName }
            val merged = freshPrime(variable)
            mergedPrimes += merged
            val arms = keyExprs.zip(perChild).joinToString(" ") { (key, expr) -> "$key : $expr;" }
            mergedConstraints += "${merged.name} = case $arms esac"
            mergedCurrent[variable] = merged.name
        }

        return NuxmvBranch(
            constraints = mergedConstraints,
            currentPrime = mergedCurrent,
            newPrimes = mergedPrimes,
            ivars = mergedIvars + childExtraIvars,
            frozenVars = mergedFrozenVars + childExtraFrozenVars,
        )
    }

    private fun resolveVariable(expression: org.eclipse.emf.ecore.EObject): VariableDeclaration {
        val ref = expression as? ElementReference
            ?: error("Unsupported reference shape on the left-hand side of an assignment: ${expression::class.simpleName}")
        val element = ref.element
        return element as? VariableDeclaration
            ?: error("Expected a variable reference on the left-hand side of an assignment, got ${element::class.simpleName}")
    }
}
