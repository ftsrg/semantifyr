package hu.bme.mit.gamma.oxsts.engine.transformation

import hu.bme.mit.gamma.oxsts.model.oxsts.AssumptionOperation
import hu.bme.mit.gamma.oxsts.model.oxsts.ChoiceOperation
import hu.bme.mit.gamma.oxsts.model.oxsts.CompositeOperation
import hu.bme.mit.gamma.oxsts.model.oxsts.LiteralBoolean
import hu.bme.mit.gamma.oxsts.model.oxsts.Operation
import hu.bme.mit.gamma.oxsts.model.oxsts.Transition
import org.eclipse.xtext.EcoreUtil2

object OperationOptimizer {

    fun optimize(transition: Transition) {
        for (operation in transition.operation) {
            optimize(operation)
        }
    }

    private fun optimize(operation: Operation) {
        var optimized: Boolean

        do {
            optimized = operation.optimize()
        } while (optimized)
    }

    private fun Operation.optimize(): Boolean {
        return optimizeSingleBranchChoices() || optimizeNoOp() || optimizeEmptyComposite()
    }

    private fun Operation.optimizeSingleBranchChoices(): Boolean {
        val choices = EcoreUtil2.getAllContentsOfType(this, ChoiceOperation::class.java).filter {
            it.operation.size == 1
        }

        if (choices.isEmpty()) {
            return false
        }

        for (choice in choices) {
            EcoreUtil2.replace(choice, choice.operation.single())
        }

        return true
    }

    private fun Operation.optimizeNoOp(): Boolean {
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

    private fun Operation.optimizeEmptyComposite(): Boolean {
        val emptyComposites = EcoreUtil2.getAllContentsOfType(this, CompositeOperation::class.java).filter {
            it.operation.isEmpty()
        }

        if (emptyComposites.isEmpty()) {
            return false
        }

        for (empty in emptyComposites) {
            EcoreUtil2.remove(empty)
        }

        return true
    }

}
