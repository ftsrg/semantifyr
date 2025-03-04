/*
 * SPDX-FileCopyrightText: 2023-2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.semantifyr.transformation.optimization

import hu.bme.mit.semantifyr.oxsts.model.oxsts.AssignmentOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AssumptionOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ChoiceOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Element
import hu.bme.mit.semantifyr.oxsts.model.oxsts.HavocOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.IfOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Operation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Property
import hu.bme.mit.semantifyr.oxsts.model.oxsts.SequenceOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Transition
import hu.bme.mit.semantifyr.oxsts.semantifyr.transformation.optimization.ExpressionOptimizer.optimizeExpressions
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.OxstsFactory
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.eAllContentsOfType
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.isConstantFalse
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.isConstantFalseOperation
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.isConstantTrue
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.isConstantTrueOperation
import org.eclipse.xtext.EcoreUtil2

object OperationOptimizer {

    fun Transition.optimize(): Transition {
        optimizeLoop()

        // optimization results in possibly consistent, but "not well-formed" model
        fixWellFormednessProblems()

        return this
    }

    fun Operation.optimize() {
        optimizeLoop()

        fixWellFormednessProblems()
    }

    fun Property.optimize() {
        optimizeLoop()
    }

    private fun Element.optimizeLoop() {
        var workRemaining = true

        while (workRemaining) {
            workRemaining = optimizeInternal()
        }
    }

    private fun Element.optimizeInternal(): Boolean {
        return replaceTrueAssumptions() ||

                removeConstantFalseChoiceElseBranches() ||
                propagateConstantFalseSequenceSteps() ||
                propagateConstantFalseChoiceBranches() ||

                rewriteConstantGuardIfs() ||
                removeRedundantChoiceElseBranch() ||

                flattenNestedSequences() ||
                flattenNestedChoices() ||

                flattenSingleBranchChoices() ||
                flattenSingleStepSequences() ||
                flattenSingleEmptyBranchIfs() ||

                removeEmptySequences() ||
                removeEmptyChoices() ||

                optimizeExpressions()
    }

    private fun Element.rewriteConstantGuardIfs(): Boolean {
        val ifOperation = eAllContentsOfType<IfOperation>().firstOrNull {
            it.guard.isConstantTrue || it.guard.isConstantFalse
        }

        if (ifOperation == null) {
            return false
        }

        if (ifOperation.guard.isConstantTrue) {
            EcoreUtil2.replace(ifOperation, ifOperation.body)
        } else if (ifOperation.`else` != null) {
            EcoreUtil2.replace(ifOperation, ifOperation.`else`)
        } else {
            EcoreUtil2.replace(ifOperation, OxstsFactory.createEmptyOperation())
        }

        return true
    }

    private fun Element.flattenSingleBranchChoices(): Boolean {
        val choice = eAllContentsOfType<ChoiceOperation>().firstOrNull {
            it.operation.size == 1 && it.`else` == null
        }

        if (choice == null) {
            return false
        }

        EcoreUtil2.replace(choice, choice.operation.single())

        return true
    }

    private fun Element.flattenSingleStepSequences(): Boolean {
        val sequence = eAllContentsOfType<SequenceOperation>().firstOrNull {
            it.operation.size == 1
        }

        if (sequence == null) {
            return false
        }

        EcoreUtil2.replace(sequence, sequence.operation.single())

        return true
    }

    private fun Element.flattenSingleEmptyBranchIfs(): Boolean {
        val singleEmptyBranchIfs = eAllContentsOfType<IfOperation>().filter {
            it.`else` == null && it.body.isEmptySequence()
        }.toList()

        if (singleEmptyBranchIfs.isEmpty()) {
            return false
        }

        for (emptySequence in singleEmptyBranchIfs) {
            EcoreUtil2.replace(emptySequence, emptySequence.body)
        }

        return true
    }

    private fun Element.flattenNestedSequences(): Boolean {
        val sequence = eAllContentsOfType<SequenceOperation>().firstOrNull {
            it.operation.filterIsInstance<SequenceOperation>().any()
        }

        if (sequence == null) {
            return false
        }

        val internalSequence = sequence.operation.filterIsInstance<SequenceOperation>().first()

        val index = sequence.operation.indexOf(internalSequence)
        sequence.operation.addAll(index, internalSequence.operation)

        EcoreUtil2.remove(internalSequence)

        return true
    }

    private fun Element.flattenNestedChoices(): Boolean {
        val choice = eAllContentsOfType<ChoiceOperation>().firstOrNull {
            it.operation.filterIsInstance<ChoiceOperation>().any {
                it.`else` == null
            }
        }

        if (choice == null) {
            return false
        }

        val internalChoice = choice.operation.filterIsInstance<ChoiceOperation>().first {
            it.`else` == null
        }

        choice.operation += internalChoice.operation

        EcoreUtil2.remove(internalChoice)

        return true
    }

    private fun Element.replaceTrueAssumptions(): Boolean {
        val assumptions = eAllContentsOfType<AssumptionOperation>().filter {
            it.isConstantTrueOperation
        }.toList()

        if (assumptions.isEmpty()) {
            return false
        }

        for (assumption in assumptions) {
            EcoreUtil2.replace(assumption, OxstsFactory.createEmptyOperation())
        }

        return true
    }

    private fun Element.propagateConstantFalseSequenceSteps(): Boolean {
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

    private fun Element.removeConstantFalseChoiceElseBranches(): Boolean {
        val choice = eAllContentsOfType<ChoiceOperation>().firstOrNull {
            it.`else` != null && it.`else`.isConstantFalseOperation
        }

        if (choice == null) {
            return false
        }

        choice.`else` = null

        return true
    }

    private fun Element.propagateConstantFalseChoiceBranches(): Boolean {
        val choice = eAllContentsOfType<ChoiceOperation>().firstOrNull {
            it.operation.any { it.isConstantFalseOperation }
        }

        if (choice == null) {
            return false
        }

        val assumption = choice.operation.first {
            it.isConstantFalseOperation
        }

        if (choice.operation.size >= 2) { // there are other branches
            EcoreUtil2.remove(assumption)
        } else if (choice.`else` != null) { // this is the only branch, but there is an else branch, which is not assume(false)
            EcoreUtil2.replace(assumption, choice.`else`)
        } else { // the whole choice is not executable
            EcoreUtil2.replace(choice, assumption)
        }

        return true
    }

    private fun Element.removeRedundantChoiceElseBranch(): Boolean {
        val redundantChoiceElse = eAllContentsOfType<ChoiceOperation>().firstOrNull { it ->
            it.`else` != null && it.operation.all { it.isAlwaysExecutable() }
        }

        if (redundantChoiceElse == null) {
            return false
        }

        redundantChoiceElse.`else` = null

        return true
    }

    private fun Element.removeEmptySequences(): Boolean {
        val emptySequences = eAllContentsOfType<SequenceOperation>().filter {
            it.isRemovableEmptySequence()
        }.toList()

        if (emptySequences.isEmpty()) {
            return false
        }

        for (emptySequence in emptySequences) {
            EcoreUtil2.remove(emptySequence)
        }

        return true
    }

    private fun Element.removeEmptyChoices(): Boolean {
        val emptyChoices = eAllContentsOfType<ChoiceOperation>().filter {
            it.operation.isEmpty() || it.operation.singleOrNull()?.isEmptySequence() == true
        }.toList()

        if (emptyChoices.isEmpty()) {
            return false
        }

        for (emptyChoice in emptyChoices) {
            EcoreUtil2.replace(emptyChoice, OxstsFactory.createEmptyOperation())
        }

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

    private fun SequenceOperation.isRemovableEmptySequence(): Boolean {
        val parent = eContainer()

        if (operation.any()) {
            return false
        }

        return when (parent) {
            is SequenceOperation -> true // should not happen
            is Transition -> parent.operation.size > 1
            is ChoiceOperation -> parent.`else` != this && parent.operation.size > 1
            is IfOperation -> parent.`else` == null || parent.`else` == this
            else -> error("Illegal container operation: $parent")
        }
    }

    private fun Operation.isEmptySequence(): Boolean {
        return this is SequenceOperation && operation.isEmpty()
    }

    private fun Transition.fixWellFormednessProblems() {
        for (operation in operation) {
            // transition operations must be wrapped in sequences
            operation.ensureWrapped()
            operation.fixWellFormednessProblems()
        }
    }

    private fun Operation.fixWellFormednessProblems() {
        // choice branch-else must be wrapped in sequences
        eAllContentsOfType<ChoiceOperation>().toList().forEach {
            it.ensureBranchesWrapped()
        }

        // if body-else must be wrapped in sequences
        eAllContentsOfType<IfOperation>().toList().forEach {
            it.ensureBranchesWrapped()
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
