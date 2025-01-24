/*
 * SPDX-FileCopyrightText: 2023-2024 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.semantifyr.transformation.optimization

import hu.bme.mit.semantifyr.oxsts.model.oxsts.AssignmentOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AssumptionOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ChoiceOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.HavocOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.IfOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Operation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.SequenceOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Transition
import hu.bme.mit.semantifyr.oxsts.semantifyr.transformation.optimization.ExpressionOptimizer.optimize
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.OxstsFactory
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.eAllContentsOfType
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.isConstantFalse
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.isConstantFalseOperation
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.isConstantTrue
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.isConstantTrueOperation
import org.eclipse.xtext.EcoreUtil2

object OperationOptimizer {

    fun Transition.optimize(): Transition {
        for (operation in operation) {
            operation.optimize()
        }

        // optimization results in possibly consistent, but "not well-formed" model
        fixWellFormednessProblems()

        return this
    }

    private fun Operation.optimize() {
        var workRemaining = true

        while (workRemaining) {
            workRemaining = optimizeInternal()
        }
    }

    private fun Operation.optimizeInternal(): Boolean {
        return removeTrueAssumptions() ||
            removeEmptySequences() ||
            removeEmptyChoices() ||
            removeEmptyIfs() ||
            removeRedundantChoiceElseBranch() ||

            rewriteSingleBranchChoices() ||
            rewriteSingleStepSequences() ||

            flattenNestedSequences() ||
            flattenNestedChoices() ||

            rewriteConstantGuardIfs() ||

            optimizeExpressions() ||

            removeConstantFalseElseBranches() ||
            propagateConstantFalseSequenceSteps() ||
            propagateConstantFalseChoiceBranches() ||
            propagateConstantFalseIfBranches()
    }

    private fun Operation.optimizeExpressions(): Boolean {
        var optimized = false

        val ifs = eAllContentsOfType<IfOperation>()
        val assumptions = eAllContentsOfType<AssumptionOperation>()
        val assignments = eAllContentsOfType<AssignmentOperation>()

        val expressions = (
            ifs.map { it.guard } +
            assumptions.map { it.expression } +
            assignments.map { it.expression }
        ).toList()

        for (expression in expressions) {
            optimized = optimized || expression.optimize()
        }

        return optimized
    }

    private fun Operation.rewriteConstantGuardIfs(): Boolean {
        val ifOperation = eAllContentsOfType<IfOperation>().firstOrNull {
            it.guard.isConstantTrue || it.guard.isConstantFalse
        }

        if (ifOperation == null) {
            return false
        }

        if (ifOperation.guard.isConstantTrue) {
            EcoreUtil2.replace(ifOperation, ifOperation.body)
        } else {
            EcoreUtil2.replace(ifOperation, ifOperation.`else`)
        }

        return true
    }

    private fun Operation.rewriteSingleBranchChoices(): Boolean {
        val choice = eAllContentsOfType<ChoiceOperation>().firstOrNull {
            it.operation.size == 1 && it.`else` == null
        }

        if (choice == null) {
            return false
        }

        EcoreUtil2.replace(choice, choice.operation.single())

        return true
    }

    private fun Operation.flattenNestedSequences(): Boolean {
        val sequence = eAllContentsOfType<SequenceOperation>().firstOrNull {
            it.operation.any { it is SequenceOperation }
        }

        if (sequence == null) {
            return false
        }

        val internalSequence = sequence.operation.first {
            it is SequenceOperation
        } as SequenceOperation

        val index = sequence.operation.indexOf(internalSequence)
        sequence.operation.addAll(index, internalSequence.operation)
        EcoreUtil2.remove(internalSequence)

        return true
    }

    private fun Operation.rewriteSingleStepSequences(): Boolean {
        val sequence = eAllContentsOfType<SequenceOperation>().firstOrNull {
            it.operation.size == 1
        }

        if (sequence == null) {
            return false
        }

        EcoreUtil2.replace(sequence, sequence.operation.single())

        return true
    }

    private fun Operation.flattenNestedChoices(): Boolean {
        val choice = eAllContentsOfType<ChoiceOperation>().firstOrNull {
            it.operation.any {
                it is ChoiceOperation && it.`else` == null
            }
        }

        if (choice == null) {
            return false
        }

        val internalChoice = choice.operation.first {
            it is ChoiceOperation && it.`else` == null
        } as ChoiceOperation

        choice.operation += internalChoice.operation

        return true
    }

    private fun Operation.removeTrueAssumptions(): Boolean {
        val assumption = eAllContentsOfType<AssumptionOperation>().firstOrNull {
            it.isConstantTrueOperation
        }

        if (assumption == null) {
            return false
        }

        EcoreUtil2.remove(assumption)

        return true
    }

    private fun Operation.propagateConstantFalseIfBranches(): Boolean {
        val ifOperation = eAllContentsOfType<IfOperation>().firstOrNull {
            it.body.isConstantFalseOperation && it.`else` != null && it.`else`.isConstantFalseOperation
        }

        if (ifOperation == null) {
            return false
        }

        EcoreUtil2.replace(ifOperation, ifOperation.body)

        return true
    }

    private fun Operation.propagateConstantFalseSequenceSteps(): Boolean {
        val sequence = eAllContentsOfType<SequenceOperation>().firstOrNull {
            it.operation.any { it.isConstantFalseOperation }
        }

        if (sequence == null) {
            return false
        }

        val assumption = sequence.operation.first {
            it.isConstantFalseOperation
        }

        EcoreUtil2.replace(sequence, assumption)

        return true
    }

    private fun Operation.removeConstantFalseElseBranches(): Boolean {
        val choice = eAllContentsOfType<ChoiceOperation>().firstOrNull {
            it.`else` != null && it.`else`.isConstantFalseOperation
        }

        if (choice == null) {
            return false
        }

        choice.`else` = null

        return true
    }

    private fun Operation.propagateConstantFalseChoiceBranches(): Boolean {
        val choice = eAllContentsOfType<ChoiceOperation>().firstOrNull {
            it.operation.any { it.isConstantFalseOperation }
        }

        if (choice == null) {
            return false
        }

        val assumption = choice.operation.first {
            it.isConstantFalseOperation
        }

        if (choice.operation.size > 1) { // there are other branches
            EcoreUtil2.remove(assumption)
        } else if (choice.`else` != null) { // this is the only branch, but there is an else branch
            EcoreUtil2.replace(assumption, choice.`else`)
        } else { // the whole choice is not executable
            EcoreUtil2.replace(choice, assumption)
        }

        return true
    }

    private fun Operation.removeRedundantChoiceElseBranch(): Boolean {
        val redundantChoiceElse = eAllContentsOfType<ChoiceOperation>().firstOrNull { it ->
            it.`else` != null && it.operation.all { it.isAlwaysExecutable() }
        }

        if (redundantChoiceElse == null) {
            return false
        }

        redundantChoiceElse.`else` = null

        return true
    }

    private fun Operation.removeEmptySequences(): Boolean {
        val emptySequence = eAllContentsOfType<SequenceOperation>().firstOrNull {
            it.operation.isEmpty() && it.isRemovable()
        }

        if (emptySequence == null) {
            return false
        }

        EcoreUtil2.remove(emptySequence)

        return true
    }

    private fun Operation.removeEmptyChoices(): Boolean {
        val emptyChoice = eAllContentsOfType<ChoiceOperation>().firstOrNull {
            it.operation.isEmpty() && it.`else` == null && it.isRemovable()
        }

        if (emptyChoice == null) {
            return false
        }

        EcoreUtil2.remove(emptyChoice)

        return true
    }

    private fun Operation.removeEmptyIfs(): Boolean {
        val emptyIf = eAllContentsOfType<IfOperation>().firstOrNull {
            it.body == null && it.`else` == null && it.isRemovable()
        }

        if (emptyIf == null) {
            return false
        }

        EcoreUtil2.remove(emptyIf)

        return true
    }

    // Upper-approximation of if this operation is never not executable.
    // False does NOT mean this operation is never executable!
    private fun Operation.isAlwaysExecutable(): Boolean {
        return when (this) {
            is AssumptionOperation -> isConstantTrueOperation
            is AssignmentOperation -> true
            is HavocOperation -> true
            is IfOperation -> {
                if (guard.isConstantFalse) {
                    true
                } else if (`else` == null) {
                    body.isAlwaysExecutable()
                } else {
                    body.isAlwaysExecutable() && `else`.isAlwaysExecutable()
                }
            }
            is ChoiceOperation -> {
                if (operation.isEmpty()) {
                    `else` == null || `else`.isAlwaysExecutable()
                } else {
                    operation.any { it.isAlwaysExecutable() } ||
                    `else`?.isAlwaysExecutable() == true
                }
            }
            is SequenceOperation -> operation.all { it.isAlwaysExecutable() }
            else -> error("Unknown operation $this")
        }
    }

    private fun Operation.isRemovable(): Boolean {
        val parent = eContainer()

        if (this is ChoiceOperation && `else` != null) {
            return false
        }

        return when (parent) {
            is ChoiceOperation -> {
                parent.`else` != this
            }
            else -> true
        }
    }

    private fun Transition.fixWellFormednessProblems() {
        for (operation in operation) {
            // transition operations must be wrapped in sequences
            operation.ensureWrapped()

            // choice branch-else must be wrapped in sequences
            operation.eAllContentsOfType<ChoiceOperation>().toList().forEach {
                it.ensureBranchesWrapped()
            }

            // if body-else must be wrapped in sequences
            operation.eAllContentsOfType<IfOperation>().toList().forEach {
                it.ensureBranchesWrapped()
            }
        }
    }

    private fun ChoiceOperation.ensureBranchesWrapped() {
        for (branch in operation) {
            branch.ensureWrapped()
        }
        `else`?.ensureWrapped()
    }

    private fun IfOperation.ensureBranchesWrapped() {
        body.ensureWrapped()
        `else`?.ensureWrapped()
    }

    private fun Operation.ensureWrapped() {
        if (this is SequenceOperation) return

        val wrapper = OxstsFactory.createSequenceOperation()
        EcoreUtil2.replace(this, wrapper)
        wrapper.operation += this
    }

}
