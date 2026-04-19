/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.optimization.patterns

import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationPattern
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.Worklist
import hu.bme.mit.semantifyr.compiler.pipeline.utils.OxstsFactory
import hu.bme.mit.semantifyr.oxsts.lang.utils.OxstsUtils
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AssumptionOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ChoiceOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.IfOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Operation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.SequenceOperation
import org.eclipse.emf.ecore.EObject
import org.eclipse.xtext.EcoreUtil2

private val Operation.isConstantFalseAssumption: Boolean
    get() = this is AssumptionOperation && OxstsUtils.isConstantLiteralFalse(expression)

private val SequenceOperation.isSingleConstantFalseAssumption: Boolean
    get() = steps.singleOrNull()?.isConstantFalseAssumption == true

class PropagateBothBranchesConstantFalsePattern : OptimizationPattern {
    override fun tryApply(element: EObject, worklist: Worklist<EObject>): Boolean {
        if (element !is IfOperation) return false
        if (!element.body.isSingleConstantFalseAssumption) return false
        if (element.`else`?.isSingleConstantFalseAssumption != true) return false
        val parent = element.eContainer() ?: return false
        EcoreUtil2.replace(element, element.body)
        worklist.add(parent)
        return true
    }
}

class PropagateConstantFalseInSequencePattern : OptimizationPattern {
    override fun tryApply(element: EObject, worklist: Worklist<EObject>): Boolean {
        if (element !is SequenceOperation) return false
        if (element.steps.size < 2) return false
        val assumption = element.steps.firstOrNull { it.isConstantFalseAssumption } ?: return false
        element.steps.clear()
        element.steps += assumption
        worklist.add(element)
        return true
    }
}

class PropagateSingleBranchConstantFalsePattern : OptimizationPattern {
    override fun tryApply(element: EObject, worklist: Worklist<EObject>): Boolean {
        if (element !is ChoiceOperation) return false
        if (element.branches.singleOrNull()?.isSingleConstantFalseAssumption != true) return false
        val parent = element.eContainer() ?: return false
        EcoreUtil2.replace(element, OxstsFactory.createAssumptionOperation(false))
        worklist.add(parent)
        return true
    }
}

class RemoveConstantFalseChoiceBranchPattern : OptimizationPattern {
    override fun tryApply(element: EObject, worklist: Worklist<EObject>): Boolean {
        if (element !is ChoiceOperation) return false
        if (element.branches.size < 2) return false
        val constantFalseBranch = element.branches.firstOrNull { it.isSingleConstantFalseAssumption } ?: return false
        EcoreUtil2.remove(constantFalseBranch)
        worklist.add(element)
        return true
    }
}
