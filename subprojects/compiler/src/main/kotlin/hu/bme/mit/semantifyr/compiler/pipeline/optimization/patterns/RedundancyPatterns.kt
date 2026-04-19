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
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ForOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.IfOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralBoolean
import org.eclipse.emf.ecore.EObject
import org.eclipse.xtext.EcoreUtil2

class ConstantGuardIfPattern : OptimizationPattern {
    override fun tryApply(element: EObject, worklist: Worklist<EObject>): Boolean {
        if (element !is IfOperation) return false
        if (element.guard !is LiteralBoolean) return false
        val parent = element.eContainer() ?: return false

        when {
            OxstsUtils.isConstantLiteralTrue(element.guard) -> {
                EcoreUtil2.replace(element, element.body)
            }
            element.`else` != null -> {
                EcoreUtil2.replace(element, element.`else`)
            }
            else -> {
                EcoreUtil2.remove(element)
            }
        }
        worklist.add(parent)
        return true
    }
}

class RemoveConstantTrueAssumptionPattern : OptimizationPattern {
    override fun tryApply(element: EObject, worklist: Worklist<EObject>): Boolean {
        if (element !is AssumptionOperation) return false
        if (!OxstsUtils.isConstantLiteralTrue(element.expression)) return false
        val parent = element.eContainer() ?: return false
        EcoreUtil2.remove(element)
        worklist.add(parent)
        return true
    }
}

class RemoveEmptyForPattern : OptimizationPattern {
    override fun tryApply(element: EObject, worklist: Worklist<EObject>): Boolean {
        if (element !is ForOperation) return false
        if (element.body.steps.isNotEmpty()) return false
        val parent = element.eContainer() ?: return false
        EcoreUtil2.remove(element)
        worklist.add(parent)
        return true
    }
}

class RemoveEmptyIfBodyPattern : OptimizationPattern {
    override fun tryApply(element: EObject, worklist: Worklist<EObject>): Boolean {
        if (element !is IfOperation) return false
        if (element.body.steps.isNotEmpty()) return false
        val parent = element.eContainer() ?: return false

        if (element.`else` == null) {
            EcoreUtil2.remove(element)
            worklist.add(parent)
        } else {
            element.guard = OxstsFactory.createNegationOperator(element.guard)
            element.body = element.`else`
            element.`else` = null
            worklist.add(element)
        }
        return true
    }
}

class RemoveEmptyIfElsePattern : OptimizationPattern {
    override fun tryApply(element: EObject, worklist: Worklist<EObject>): Boolean {
        if (element !is IfOperation) return false
        if (element.`else`?.steps?.isEmpty() != true) return false
        element.`else` = null
        worklist.add(element)
        return true
    }
}

class RemoveRedundantEmptyChoiceBranchPattern : OptimizationPattern {
    override fun tryApply(element: EObject, worklist: Worklist<EObject>): Boolean {
        if (element !is ChoiceOperation) return false
        val emptyBranches = element.branches.filter { it.steps.isEmpty() }
        if (emptyBranches.size < 2) return false
        EcoreUtil2.remove(emptyBranches.first())
        worklist.add(element)
        return true
    }
}
