/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.semantics.optimization

import com.google.inject.Inject
import com.google.inject.Singleton
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ChoiceOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Element
import hu.bme.mit.semantifyr.oxsts.model.oxsts.SequenceOperation
import hu.bme.mit.semantifyr.semantics.transformation.serializer.CompilationArtifactSaver
import hu.bme.mit.semantifyr.semantics.utils.eAllOfType
import org.eclipse.xtext.EcoreUtil2

@Singleton
class OperationFlattenerOptimizer : AbstractLoopedOptimizer<Element>() {

    @Inject
    private lateinit var compilationArtifactSaver: CompilationArtifactSaver

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

        compilationArtifactSaver.commitModelState()

        return true
    }

    private fun flattenNestedChoiceOperations(element: Element): Boolean {
        val choiceOperation = element.eAllOfType<ChoiceOperation>().firstOrNull {
            it.branches.mapNotNull { // filter to branches that are single steps ...
                it.steps.singleOrNull()
            }.filterIsInstance<ChoiceOperation>().any { // ... which single step is a choice without an else branch
                it.`else` == null
            }
        }

        if (choiceOperation == null) {
            return false
        }

        val internalChoice = choiceOperation.branches.mapNotNull { // filter to branches that are single steps ...
            it.steps.singleOrNull()
        }.filterIsInstance<ChoiceOperation>().first { // ... which single step is a choice without an else branch
            it.`else` == null
        }

        choiceOperation.branches += internalChoice.branches

        EcoreUtil2.remove(internalChoice)

        compilationArtifactSaver.commitModelState()

        return true
    }

    private fun flattenSingleBranchChoiceOperations(element: Element): Boolean {
        val choiceOperation = element.eAllOfType<ChoiceOperation>().firstOrNull {
            it.branches.size == 1 && it.`else` == null
        }

        if (choiceOperation == null) {
            return false
        }

        EcoreUtil2.replace(choiceOperation, choiceOperation.branches.single())

        compilationArtifactSaver.commitModelState()

        return true
    }

}
