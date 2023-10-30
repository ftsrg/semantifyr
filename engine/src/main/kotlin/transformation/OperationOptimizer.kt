package hu.bme.mit.gamma.oxsts.engine.transformation

import hu.bme.mit.gamma.oxsts.model.oxsts.AssignmentOperation
import hu.bme.mit.gamma.oxsts.model.oxsts.AssumptionOperation
import hu.bme.mit.gamma.oxsts.model.oxsts.ChoiceOperation
import hu.bme.mit.gamma.oxsts.model.oxsts.HavocOperation
import hu.bme.mit.gamma.oxsts.model.oxsts.IfOperation
import hu.bme.mit.gamma.oxsts.model.oxsts.LiteralBoolean
import hu.bme.mit.gamma.oxsts.model.oxsts.Operation
import hu.bme.mit.gamma.oxsts.model.oxsts.SequenceOperation
import hu.bme.mit.gamma.oxsts.model.oxsts.Transition
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
                removeEmptySequences() ||
                removeEmptyChoices() ||
                removeEmptyIfs() ||
                removeRedundantChoiceElse() ||
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
        val choices = EcoreUtil2.getAllContentsOfType(this, ChoiceOperation::class.java).filter {
            it.operation.size == 1 && it.`else` == null
        }

        if (choices.isEmpty()) {
            return false
        }

        for (choice in choices) {
            EcoreUtil2.replace(choice, choice.operation.single())
        }

        return true
    }

    private fun Operation.removeTrueAssumptions(): Boolean {
        val noOps = EcoreUtil2.getAllContentsOfType(this, AssumptionOperation::class.java).filter {
            it.expression is LiteralBoolean && (it.expression as LiteralBoolean).isValue
        }

        if (noOps.isEmpty()) {
            return false
        }

        for (noOp in noOps) {
            EcoreUtil2.remove(noOp)
        }

        return true
    }

    private fun Operation.removeEmptyIfs(): Boolean {
        val emptyIfs = EcoreUtil2.getAllContentsOfType(this, IfOperation::class.java).filter {
            it.body == null && it.`else` == null
        }

        if (emptyIfs.isEmpty()) {
            return false
        }

        for (empty in emptyIfs) {
            EcoreUtil2.remove(empty)
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

        for (empty in emptySequences) {
            EcoreUtil2.remove(empty)
        }

        return true
    }

    private fun Operation.removeEmptyChoices(): Boolean {
        val emptyChoices = EcoreUtil2.getAllContentsOfType(this, ChoiceOperation::class.java).filter {
            it.operation.isEmpty() && it.`else` == null && it.isRemovable()
        }

        if (emptyChoices.isEmpty()) {
            return false
        }

        for (empty in emptyChoices) {
            EcoreUtil2.remove(empty)
        }

        return true
    }

    private fun Operation.removeRedundantChoiceElse(): Boolean {
        val redundantChoiceElse = EcoreUtil2.getAllContentsOfType(this, ChoiceOperation::class.java).filter {
            it.`else` != null && it.operation.all { it.isAlwaysExecutable() }
        }

        if (redundantChoiceElse.isEmpty()) {
            return false
        }

        for (choice in redundantChoiceElse) {
            choice.`else` = null
        }

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

        return when (parent) {
            is ChoiceOperation -> {
                parent.`else` != this
            }
            else -> true
        }
    }

}
