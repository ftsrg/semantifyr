/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.semantics.optimization

import com.google.inject.Inject
import hu.bme.mit.semantifyr.oxsts.lang.utils.OxstsUtils
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AssumptionOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ChoiceOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Element
import hu.bme.mit.semantifyr.oxsts.model.oxsts.IfOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Operation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.SequenceOperation
import hu.bme.mit.semantifyr.semantics.scope.CompilationScoped
import hu.bme.mit.semantifyr.semantics.artifact.CompilationArtifactManager
import hu.bme.mit.semantifyr.semantics.artifact.CompilationPass
import hu.bme.mit.semantifyr.semantics.utils.OxstsFactory
import hu.bme.mit.semantifyr.semantics.utils.eAllOfType
import org.eclipse.xtext.EcoreUtil2

private val Operation.isConstantFalseAssumption
    get() = this is AssumptionOperation && OxstsUtils.isConstantLiteralFalse(expression)

private val SequenceOperation.isSingleConstantFalseAssumption
    get() = steps.singleOrNull()?.isConstantFalseAssumption == true

@CompilationScoped
class ConstantFalseAssumptionPropagatorOptimizer @Inject constructor(
    private val config: OptimizationConfig,
    private val compilationArtifactManager: CompilationArtifactManager,
) : AbstractLoopedOptimizer<Element>() {

    override fun doOptimizationStep(element: Element): Boolean {
        if (!config.isAnyEnabled(OptimizationCategory.AssumptionPropagation)) {
            return false
        }
        return propagateInSequenceOperation(element)
                || removeInChoiceBranch(element)
                || propagateSingleBranchChoiceOperation(element)
                || propagateIfOperation(element)
    }

    private fun propagateInSequenceOperation(element: Element): Boolean {
        val sequenceOperation = element.eAllOfType<SequenceOperation>().firstOrNull {
            it.steps.size >= 2 && it.steps.any { it.isConstantFalseAssumption }
        }

        if (sequenceOperation == null) {
            return false
        }

        val assumption = sequenceOperation.steps.first { it.isConstantFalseAssumption }

        sequenceOperation.steps.clear()
        sequenceOperation.steps += assumption

        compilationArtifactManager.commitStep(CompilationPass.AssumptionPropagation)

        return true
    }

    private fun removeInChoiceBranch(element: Element): Boolean {
        val choiceOperation = element.eAllOfType<ChoiceOperation>().firstOrNull {
            it.branches.size >= 2 && it.branches.any { it.isSingleConstantFalseAssumption }
        }

        if (choiceOperation == null) {
            return false
        }

        val constantFalseBranch = choiceOperation.branches.first { it.isSingleConstantFalseAssumption }

        EcoreUtil2.remove(constantFalseBranch)

        compilationArtifactManager.commitStep(CompilationPass.AssumptionPropagation)

        return true
    }

    private fun propagateSingleBranchChoiceOperation(element: Element): Boolean {
        val choiceOperation = element.eAllOfType<ChoiceOperation>().firstOrNull {
            it.branches.singleOrNull()?.isSingleConstantFalseAssumption == true
        }

        if (choiceOperation == null) {
            return false
        }

        EcoreUtil2.replace(choiceOperation, OxstsFactory.createAssumptionOperation(false))

        compilationArtifactManager.commitStep(CompilationPass.AssumptionPropagation)

        return true
    }

    private fun propagateIfOperation(element: Element): Boolean {
        val ifOperation = element.eAllOfType<IfOperation>().firstOrNull {
            it.body.isSingleConstantFalseAssumption && it.`else`?.isSingleConstantFalseAssumption == true
        }

        if (ifOperation == null) {
            return false
        }

        EcoreUtil2.replace(ifOperation, ifOperation.body)

        compilationArtifactManager.commitStep(CompilationPass.AssumptionPropagation)

        return true
    }

}
