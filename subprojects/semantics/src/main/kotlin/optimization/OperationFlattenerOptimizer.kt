/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.semantics.optimization

import com.google.inject.Inject
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ChoiceOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Element
import hu.bme.mit.semantifyr.oxsts.model.oxsts.SequenceOperation
import hu.bme.mit.semantifyr.semantics.transformation.injection.scope.CompilationScoped
import hu.bme.mit.semantifyr.semantics.transformation.serializer.CompilationStateManager
import hu.bme.mit.semantifyr.semantics.utils.eAllOfType
import org.eclipse.xtext.EcoreUtil2

@CompilationScoped
class OperationFlattenerOptimizer : AbstractLoopedOptimizer<Element>() {

    @Inject
    private lateinit var compilationStateManager: CompilationStateManager

    override fun doOptimizationStep(element: Element): Boolean {
        return flattenNestedSequenceOperations(element)
            || flattenNestedChoiceOperations(element)
            || flattenSingleBranchChoiceOperations(element)
    }

    private fun flattenNestedSequenceOperations(element: Element): Boolean {
        val sequenceOperation = element.eAllOfType<SequenceOperation>().firstOrNull {
            it.steps.filterIsInstance<SequenceOperation>().any()
        }

        if (sequenceOperation == null) {
            return false
        }

        val nestedSequenceOperation = sequenceOperation.steps.filterIsInstance<SequenceOperation>().first()

        val index = sequenceOperation.steps.indexOf(nestedSequenceOperation)
        sequenceOperation.steps.addAll(index, nestedSequenceOperation.steps)

        EcoreUtil2.remove(nestedSequenceOperation)

        compilationStateManager.commitModelState()

        return true
    }

    private fun flattenNestedChoiceOperations(element: Element): Boolean {
        val choiceOperation = element.eAllOfType<ChoiceOperation>().firstOrNull {
            it.branches.mapNotNull {
                it.steps.singleOrNull()
            }.filterIsInstance<ChoiceOperation>().any()
        }

        if (choiceOperation == null) {
            return false
        }

        val internalChoice = choiceOperation.branches.mapNotNull {
            it.steps.singleOrNull()
        }.filterIsInstance<ChoiceOperation>().first()

        val containerSequence = internalChoice.eContainer()
        choiceOperation.branches += internalChoice.branches

        EcoreUtil2.remove(containerSequence)

        compilationStateManager.commitModelState()

        return true
    }

    private fun flattenSingleBranchChoiceOperations(element: Element): Boolean {
        val choiceOperation = element.eAllOfType<ChoiceOperation>().firstOrNull {
            it.branches.size == 1
        }

        if (choiceOperation == null) {
            return false
        }

        EcoreUtil2.replace(choiceOperation, choiceOperation.branches.single())

        compilationStateManager.commitModelState()

        return true
    }

}
