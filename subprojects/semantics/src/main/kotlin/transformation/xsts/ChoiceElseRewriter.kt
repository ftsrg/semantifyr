/*
 * SPDX-FileCopyrightText: 2023-2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.semantics.transformation.xsts

import com.google.inject.Inject
import com.google.inject.Singleton
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AssignmentOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AssumptionOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.BooleanOp
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ChoiceOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ForOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.HavocOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.IfOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Operation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.SequenceOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.TransitionDeclaration
import hu.bme.mit.semantifyr.semantics.optimization.XstsExpressionOptimizer
import hu.bme.mit.semantifyr.semantics.utils.OxstsFactory
import hu.bme.mit.semantifyr.semantics.utils.allBranches
import hu.bme.mit.semantifyr.semantics.utils.copy

@Singleton
class ChoiceElseRewriter {

    @Inject
    private lateinit var xstsExpressionOptimizer: XstsExpressionOptimizer

    fun rewriteChoiceElse(declaration: TransitionDeclaration) {
        for (branch in declaration.branches) {
            rewriteChoiceElse(branch)
        }
    }

    private fun rewriteChoiceElse(operation: Operation) {
        when (operation) {
            is SequenceOperation -> operation.steps.forEach {
                rewriteChoiceElse(it)
            }
            is ChoiceOperation -> operation.allBranches.forEach {
                rewriteChoiceElse(it)
            }
            is IfOperation -> {
                rewriteChoiceElse(operation.body)
                if (operation.`else` != null) {
                    rewriteChoiceElse(operation.`else`)
                }
            }
            is ForOperation -> {
                rewriteChoiceElse(operation.body)
            }
        }

        if (operation is ChoiceOperation) {
            doRewriteChoiceElse(operation)
        }
    }

    private fun doRewriteChoiceElse(operation: ChoiceOperation) {
        if (operation.`else` == null) {
            return
        }

        val operationElse = operation.`else`
        operation.`else` = null

        val assumptionExpression = calculateAssumption(operation)
        val negatedAssumptionExpression = OxstsFactory.createNegationOperator(assumptionExpression)

        val choiceElseAssumption = OxstsFactory.createAssumptionOperation(negatedAssumptionExpression)

        xstsExpressionOptimizer.optimize(choiceElseAssumption)

        operationElse.steps.add(0, choiceElseAssumption)

        operation.branches += operationElse
    }

    private fun calculateAssumption(operation: Operation): Expression {
        return when (operation) {
            is AssumptionOperation -> operation.expression.copy()
            is AssignmentOperation -> OxstsFactory.createLiteralBoolean(true)
            is HavocOperation -> OxstsFactory.createLiteralBoolean(true)
            is SequenceOperation -> calculateAssumption(operation)
            is ChoiceOperation -> calculateAssumption(operation)
            is IfOperation -> calculateAssumption(operation)
            is ForOperation -> calculateAssumption(operation)
            else -> error("Unknown operation: $this!")
        }
    }

    private fun calculateAssumption(operation: SequenceOperation): Expression {
        return operation.steps.map {
            calculateAssumption(it)
        }.fold<Expression, Expression>(OxstsFactory.createLiteralBoolean(true)) { left, right ->
            OxstsFactory.createBooleanOperator(BooleanOp.OR, left, right)
        }
    }

    private fun calculateAssumption(operation: ChoiceOperation): Expression {
        return operation.branches.map {
            calculateAssumption(it)
        }.fold<Expression, Expression>(OxstsFactory.createLiteralBoolean(true)) { left, right ->
            OxstsFactory.createBooleanOperator(BooleanOp.OR, left, right)
        }
    }

    private fun calculateAssumption(operation: IfOperation): Expression {
        val guardAssumption = operation.guard.copy()
        val notGuardAssumption = OxstsFactory.createNegationOperator(operation.guard.copy())
        val bodyAssumption = calculateAssumption(operation.body)
        val elseAssumption = if (operation.`else` != null) {
            calculateAssumption(operation.`else`)
        } else {
            OxstsFactory.createLiteralBoolean(true)
        }

        // it can be executed, if the guard is true and the body can be executed,
        //  or the guard is false and the else can be executed
        return OxstsFactory.createBooleanOperator(
            BooleanOp.OR,
            OxstsFactory.createBooleanOperator(BooleanOp.AND, guardAssumption, bodyAssumption),
            OxstsFactory.createBooleanOperator(BooleanOp.AND, notGuardAssumption, elseAssumption),
        )
    }

    private fun calculateAssumption(operation: ForOperation): Expression {
        // TODO: what to calculate here?
        return OxstsFactory.createLiteralBoolean(false)
    }

}
