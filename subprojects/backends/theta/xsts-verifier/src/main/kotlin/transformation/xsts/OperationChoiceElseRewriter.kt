/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.theta.verification.transformation.xsts

import com.google.inject.Inject
import hu.bme.mit.semantifyr.oxsts.lang.utils.OperationVisitor
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AssignmentOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AssumptionOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ChoiceOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ForOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.HavocOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.IfOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlineCall
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlineChoiceFor
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlineIfOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlineSeqFor
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LocalVarDeclarationOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Operation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.SequenceOperation
import hu.bme.mit.semantifyr.semantics.optimization.XstsExpressionOptimizer
import hu.bme.mit.semantifyr.semantics.transformation.serializer.CompilationStateManager
import hu.bme.mit.semantifyr.semantics.utils.OxstsFactory

class OperationChoiceElseRewriter : OperationVisitor<Unit>() {

    @Inject
    private lateinit var xstsExpressionOptimizer: XstsExpressionOptimizer

    @Inject
    private lateinit var compilationStateManager: CompilationStateManager

    @Inject
    private lateinit var operationAssumptionCalculator: OperationAssumptionCalculator

    fun rewriteChoiceElse(operation: Operation) {
        visit(operation)
    }

    override fun visit(operation: SequenceOperation) {
        for (step in operation.steps) {
            visit(step)
        }
    }

    override fun visit(operation: ChoiceOperation) {
        for (branch in operation.branches) {
            visit(branch)
        }

        if (operation.`else` != null) {
            visit(operation.`else`)
            doRewriteChoiceElse(operation)
        }
    }

    override fun visit(operation: LocalVarDeclarationOperation) {
        // NO-OP
    }

    override fun visit(operation: ForOperation) {
        visit(operation.body)
    }

    override fun visit(operation: IfOperation) {
        visit(operation.body)
        if (operation.`else` != null) {
            visit(operation.`else`)
        }
    }

    override fun visit(operation: HavocOperation) {
        // NO-OP
    }

    override fun visit(operation: AssumptionOperation) {
        // NO-OP
    }

    override fun visit(operation: AssignmentOperation) {
        // NO-OP
    }

    override fun visit(operation: InlineCall) {
        // NO-OP
    }

    override fun visit(operation: InlineIfOperation) {
        // NO-OP
    }

    override fun visit(operation: InlineSeqFor) {
        // NO-OP
    }

    override fun visit(operation: InlineChoiceFor) {
        // NO-OP
    }

    private fun doRewriteChoiceElse(operation: ChoiceOperation) {
        val operationElse = operation.`else`
        operation.`else` = null

        val assumptionExpression = operationAssumptionCalculator.calculateAssumption(operation)
        val negatedAssumptionExpression = OxstsFactory.createNegationOperator(assumptionExpression)
        val choiceElseAssumption = OxstsFactory.createAssumptionOperation(negatedAssumptionExpression)

        operationElse.steps.add(0, choiceElseAssumption)

        operation.branches += operationElse

        compilationStateManager.commitModelState()

        xstsExpressionOptimizer.optimize(choiceElseAssumption)
    }

}
