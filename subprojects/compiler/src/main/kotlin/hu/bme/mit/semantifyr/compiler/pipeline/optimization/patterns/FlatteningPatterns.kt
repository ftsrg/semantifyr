/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.optimization.patterns

import hu.bme.mit.semantifyr.compiler.pipeline.optimization.CompositeOptimizationPattern
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationPattern
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.Worklist
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ChoiceOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.SequenceOperation
import org.eclipse.emf.ecore.EObject
import org.eclipse.xtext.EcoreUtil2

class FlatteningPattern : CompositeOptimizationPattern() {

    override val patterns: Collection<OptimizationPattern> = listOf(
        FlattenNestedChoicePattern(),
        FlattenNestedSequencePattern(),
        FlattenSingleBranchChoicePattern(),
    )

}

class FlattenNestedChoicePattern : OptimizationPattern {
    override fun tryApply(element: EObject, worklist: Worklist<EObject>): Boolean {
        if (element !is ChoiceOperation) {
            return false
        }
        val internalChoice = element.branches.mapNotNull {
            it.steps.singleOrNull()
        }.filterIsInstance<ChoiceOperation>().firstOrNull() ?: return false

        val containerSequence = internalChoice.eContainer()
        element.branches += internalChoice.branches
        EcoreUtil2.remove(containerSequence)
        worklist.add(element)
        return true
    }
}

class FlattenNestedSequencePattern : OptimizationPattern {
    override fun tryApply(element: EObject, worklist: Worklist<EObject>): Boolean {
        if (element !is SequenceOperation) {
            return false
        }
        val nested = element.steps.filterIsInstance<SequenceOperation>().firstOrNull() ?: return false

        val index = element.steps.indexOf(nested)
        element.steps.addAll(index, nested.steps)
        EcoreUtil2.remove(nested)
        worklist.add(element)
        return true
    }
}

class FlattenSingleBranchChoicePattern : OptimizationPattern {
    override fun tryApply(element: EObject, worklist: Worklist<EObject>): Boolean {
        if (element !is ChoiceOperation) {
            return false
        }
        if (element.branches.size != 1) {
            return false
        }
        val parent = element.eContainer() ?: return false

        EcoreUtil2.replace(element, element.branches.single())
        worklist.add(parent)
        return true
    }
}
