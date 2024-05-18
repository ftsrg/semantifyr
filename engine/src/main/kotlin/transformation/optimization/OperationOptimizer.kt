package hu.bme.mit.semantifyr.oxsts.engine.transformation.optimization

import hu.bme.mit.semantifyr.oxsts.model.oxsts.AssignmentOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AssumptionOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ChoiceOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.HavocOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.IfOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralBoolean
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Operation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.SequenceOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Transition
import org.eclipse.xtext.EcoreUtil2

object OperationOptimizer {

    fun optimize(transition: Transition): Boolean {
        var optimized = false

        for (operation in transition.operation) {
            optimized = optimized || optimize(operation)
        }

        return optimized
    }

    private fun optimize(operation: Operation): Boolean {
        var anyOptimization = false
        var optimized: Boolean

        do {
            optimized = operation.optimizeInternal()
            anyOptimization = anyOptimization || optimized
        } while (optimized)

        return anyOptimization
    }

    private fun Operation.optimizeInternal(): Boolean {
        return rewriteSingleBranchChoices() ||
                removeTrueAssumptions() ||
                propagateFalseAssumptions() ||
                removeEmptySequences() ||
                removeEmptyChoices() ||
                removeEmptyIfs() ||
                removeRedundantChoiceElse() ||
                flattenSequences() ||
                flattenChoices() ||
                removeFalseElseBranches() ||
                removeFalseBranches() ||
                optimizeExpressions()
    }

    private fun Operation.optimizeExpressions(): Boolean {
        var optimized = false

        val ifs = EcoreUtil2.getAllContentsOfType(this, IfOperation::class.java)
        val assumptions = EcoreUtil2.getAllContentsOfType(this, AssumptionOperation::class.java)
        val assignments = EcoreUtil2.getAllContentsOfType(this, AssignmentOperation::class.java)

        val expressions = ifs.map { it.guard } + assumptions.map { it.expression } + assignments.map { it.expression }

        for (expression in expressions) {
            optimized = optimized || ExpressionOptimizer.optimize(expression)
        }

        return optimized
    }

    private fun Operation.rewriteSingleBranchChoices(): Boolean {
        val choice = EcoreUtil2.getAllContentsOfType(this, ChoiceOperation::class.java).firstOrNull {
            it.operation.size == 1 && it.`else` == null
        }

        if (choice == null) {
            return false
        }

        EcoreUtil2.replace(choice, choice.operation.single())

        return true
    }

    private fun Operation.flattenSequences(): Boolean {
        val sequence = EcoreUtil2.getAllContentsOfType(this, SequenceOperation::class.java).firstOrNull {
            it.operation.size == 1
        }

        if (sequence == null) {
            return false
        }

        EcoreUtil2.replace(sequence, sequence.operation.single())

        return true
    }

    private fun Operation.flattenChoices(): Boolean {
        val choice = EcoreUtil2.getAllContentsOfType(this, ChoiceOperation::class.java).firstOrNull {
            it.eContainer() is ChoiceOperation && it.isRemovable()
        }

        if (choice == null) {
            return false
        }

        val container = choice.eContainer() as ChoiceOperation
        container.operation += choice.operation

        return true
    }

    private fun Operation.removeTrueAssumptions(): Boolean {
        val assumptions = EcoreUtil2.getAllContentsOfType(this, AssumptionOperation::class.java).filter {
            it.isTrueOperation
        }

        if (assumptions.isEmpty()) {
            return false
        }

        for (assumption in assumptions) {
            EcoreUtil2.remove(assumption)
        }

        return true
    }

    private fun Operation.propagateFalseAssumptions(): Boolean {
        val falseAssumption = EcoreUtil2.getAllContentsOfType(this, AssumptionOperation::class.java).firstOrNull {
            it.isFalseOperation
        }

        if (falseAssumption == null) {
            return false
        }

        val parent = falseAssumption.eContainer()

        if (parent !is SequenceOperation) {
            return false
        }

        EcoreUtil2.replace(parent, falseAssumption)

        return true
    }

    private fun Operation.removeEmptyIfs(): Boolean {
        val emptyIfs = EcoreUtil2.getAllContentsOfType(this, IfOperation::class.java).filter {
            it.body == null && it.`else` == null
        }

        if (emptyIfs.isEmpty()) {
            return false
        }

        for (emptyIf in emptyIfs) {
            EcoreUtil2.remove(emptyIf)
        }

        return true
    }

    private fun Operation.removeFalseElseBranches(): Boolean {
        val choices = EcoreUtil2.getAllContentsOfType(this, ChoiceOperation::class.java).filter {
            it.`else`.let {
                it is AssumptionOperation && it.isFalseOperation
            }
        }

        if (choices.isEmpty()) {
            return false
        }

        for (choice in choices) {
            choice.`else` = null
        }

        return true
    }

    private fun Operation.removeFalseBranches(): Boolean {
        val choice = EcoreUtil2.getAllContentsOfType(this, ChoiceOperation::class.java).firstOrNull {
            it.operation.any {
                it is AssumptionOperation && it.isFalseOperation
            }
        }

        if (choice == null) {
            return false
        }

        val assumption = choice.operation.first { it is AssumptionOperation && it.isFalseOperation }

        if (choice.operation.size > 1) {
            EcoreUtil2.remove(assumption)
        } else if (choice.`else` != null) {
            EcoreUtil2.replace(assumption, choice.`else`)
        } else {
            EcoreUtil2.replace(choice, assumption)
        }

        return true
    }

    private fun Operation.removeEmptySequences(): Boolean {
        val emptySequences = EcoreUtil2.getAllContentsOfType(this, SequenceOperation::class.java).filter {
            it.operation.isEmpty() && it.isRemovable()
        }

        if (emptySequences.isEmpty()) {
            return false
        }

        for (emptySequence in emptySequences) {
            EcoreUtil2.remove(emptySequence)
        }

        return true
    }

    private fun Operation.removeEmptyChoices(): Boolean {
        val emptyChoices = EcoreUtil2.getAllContentsOfType(this, ChoiceOperation::class.java).filter {
            it.operation.isEmpty() && it.isRemovable()
        }

        if (emptyChoices.isEmpty()) {
            return false
        }

        for (emptyChoice in emptyChoices) {
            EcoreUtil2.remove(emptyChoice)
        }

        return true
    }

    private fun Operation.removeRedundantChoiceElse(): Boolean {
        val redundantChoiceElse = EcoreUtil2.getAllContentsOfType(this, ChoiceOperation::class.java).firstOrNull { it ->
            it.`else` != null && it.operation.all { it.isAlwaysExecutable() }
        }

        if (redundantChoiceElse == null) {
            return false
        }

        redundantChoiceElse.`else` = null

        return true
    }

    private fun Operation.isAlwaysExecutable(): Boolean {
        return when (this) {
            is AssumptionOperation -> false
            is AssignmentOperation -> true
            is HavocOperation -> true
            is IfOperation -> body.isAlwaysExecutable() || (`else`?.isAlwaysExecutable() ?: false)
            is ChoiceOperation -> {
                (operation.map { it.isAlwaysExecutable() }.reduceOrNull { l, r -> l || r } ?: true) || (`else`?.isAlwaysExecutable() ?: false)
            }
            is SequenceOperation -> operation.map { it.isAlwaysExecutable() }.reduceOrNull { l, r -> l && r } ?: true
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

}

private val AssumptionOperation.isFalseOperation
    get() = expression is LiteralBoolean && (expression as LiteralBoolean).isValue == false

private val AssumptionOperation.isTrueOperation
    get() = expression is LiteralBoolean && (expression as LiteralBoolean).isValue
