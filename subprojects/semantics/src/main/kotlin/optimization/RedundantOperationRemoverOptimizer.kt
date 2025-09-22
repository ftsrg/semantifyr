/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.semantics.optimization

import com.google.inject.Inject
import com.google.inject.Singleton
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AssignmentOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AssumptionOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ChoiceOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Element
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ForOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.HavocOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.IfOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralBoolean
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Operation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.SequenceOperation
import hu.bme.mit.semantifyr.semantics.transformation.serializer.CompilationArtifactSaver
import hu.bme.mit.semantifyr.semantics.utils.OxstsFactory
import hu.bme.mit.semantifyr.semantics.utils.allBranches
import hu.bme.mit.semantifyr.semantics.utils.eAllOfType
import hu.bme.mit.semantifyr.semantics.utils.isConstantLiteralTrue
import org.eclipse.xtext.EcoreUtil2

@Singleton
class RedundantOperationRemoverOptimizer : AbstractLoopedOptimizer<Element>() {

    @Inject
    private lateinit var compilationArtifactSaver: CompilationArtifactSaver

    override fun doOptimizationStep(element: Element): Boolean {
        return removeConstantTrueAssumptions(element)
            || removeRedundantChoiceElse(element)
            || removeRedundantEmptyChoiceBranches(element)
            || removeEmptyForOperations(element)
            || removeEmptyIfElseBranches(element)
            || removeEmptyIfBodyBranches(element)
            || rewriteConstantGuardIfOperations(element)
    }

    private fun removeConstantTrueAssumptions(element: Element): Boolean {
        val constantTrueAssumptions = element.eAllOfType<AssumptionOperation>().filter {
            it.expression.isConstantLiteralTrue
        }.toList()

        if (constantTrueAssumptions.isEmpty()) {
            return false
        }

        for (assumption in constantTrueAssumptions) {
            EcoreUtil2.remove(assumption)
        }

        compilationArtifactSaver.commitModelState()

        return true
    }

    private fun removeRedundantChoiceElse(element: Element): Boolean {
        val redundantChoiceElse = element.eAllOfType<ChoiceOperation>().firstOrNull {
            it.`else` != null && it.branches.any { it.isAlwaysExecutable() }
        }

        if (redundantChoiceElse == null) {
            return false
        }

        redundantChoiceElse.`else` = null

        compilationArtifactSaver.commitModelState()

        return true
    }

    private fun removeRedundantEmptyChoiceBranches(element: Element): Boolean {
        val redundantChoiceBranch = element.eAllOfType<ChoiceOperation>().firstOrNull {
            it.branches.count { it.steps.isEmpty() } >= 2
        }

        if (redundantChoiceBranch == null) {
            return false
        }

        val redundantBranch = redundantChoiceBranch.branches.first { it.steps.isEmpty() }

        EcoreUtil2.remove(redundantBranch)

        compilationArtifactSaver.commitModelState()

        return true
    }

    private fun removeEmptyForOperations(element: Element): Boolean {
        val emptyForOperations = element.eAllOfType<ForOperation>().filter {
            it.body.steps.isEmpty()
        }.toList()

        if (emptyForOperations.isEmpty()) {
            return false
        }

        for (emptyForOperation in emptyForOperations) {
            EcoreUtil2.remove(emptyForOperation)
        }

        compilationArtifactSaver.commitModelState()

        return true
    }

    private fun removeEmptyIfElseBranches(element: Element): Boolean {
        val emptyIfElseBranches = element.eAllOfType<IfOperation>().filter {
            it.`else`?.steps?.isEmpty() == true
        }.toList()

        if (emptyIfElseBranches.isEmpty()) {
            return false
        }

        for (emptyIfElseBranch in emptyIfElseBranches) {
            emptyIfElseBranch.`else` = null
        }

        compilationArtifactSaver.commitModelState()

        return true
    }

    private fun removeEmptyIfBodyBranches(element: Element): Boolean {
        val emptyIfBodyBranch = element.eAllOfType<IfOperation>().firstOrNull {
            it.body.steps.isEmpty()
        }

        if (emptyIfBodyBranch == null) {
            return false
        }

        if (emptyIfBodyBranch.`else` == null) {
            EcoreUtil2.remove(emptyIfBodyBranch)
        } else {
            emptyIfBodyBranch.guard = OxstsFactory.createNegationOperator(emptyIfBodyBranch.guard)
            emptyIfBodyBranch.body = emptyIfBodyBranch.`else`
        }

        compilationArtifactSaver.commitModelState()

        return true
    }

    private fun rewriteConstantGuardIfOperations(element: Element): Boolean {
        val ifOperation = element.eAllOfType<IfOperation>().firstOrNull {
            it.guard is LiteralBoolean
        }

        if (ifOperation == null) {
            return false
        }

        if (ifOperation.guard.isConstantLiteralTrue) {
            EcoreUtil2.replace(ifOperation, ifOperation.body)
        } else if (ifOperation.`else` != null) {
            EcoreUtil2.replace(ifOperation, ifOperation.`else`)
        } else {
            EcoreUtil2.remove(ifOperation)
        }

        compilationArtifactSaver.commitModelState()

        return true
    }

}

// Upper-approximation of if this operation is never not executable.
// False does NOT mean this operation is never executable!
private fun Operation.isAlwaysExecutable(): Boolean {
    return when (this) {
        is AssumptionOperation -> expression.isConstantLiteralTrue
        is AssignmentOperation -> true
        is HavocOperation -> true
        is ForOperation -> body.isAlwaysExecutable()
        is SequenceOperation -> steps.all { it.isAlwaysExecutable() }
        is ChoiceOperation -> allBranches.any { it.isAlwaysExecutable() }
        is IfOperation -> body.isAlwaysExecutable() && (`else` == null || `else`.isAlwaysExecutable())
        else -> error("Unknown operation $this")
    }
}
