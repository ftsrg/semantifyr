/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.theta.verification.transformation.xsts

import hu.bme.mit.semantifyr.oxsts.lang.utils.OperationVisitor
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AssignmentOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AssumptionOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.BooleanOp
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ChoiceOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
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
import hu.bme.mit.semantifyr.semantics.utils.OxstsFactory
import hu.bme.mit.semantifyr.semantics.utils.copy

class OperationAssumptionCalculator : OperationVisitor<Expression>() {

    fun calculateAssumption(operation: Operation): Expression {
        return visit(operation)
    }

    override fun visit(operation: SequenceOperation): Expression {
        return operation.steps.map {
            visit(it)
        }.fold<Expression, Expression>(OxstsFactory.createLiteralBoolean(true)) { left, right ->
            OxstsFactory.createBooleanOperator(BooleanOp.AND, left, right)
        }
    }

    override fun visit(operation: ChoiceOperation): Expression {
        return operation.branches.map {
            visit(it)
        }.fold<Expression, Expression>(OxstsFactory.createLiteralBoolean(false)) { left, right ->
            OxstsFactory.createBooleanOperator(BooleanOp.OR, left, right)
        }
    }

    override fun visit(operation: LocalVarDeclarationOperation): Expression {
        return OxstsFactory.createLiteralBoolean(true)
    }

    override fun visit(operation: ForOperation): Expression {
        return OxstsFactory.createLiteralBoolean(true)
    }

    override fun visit(operation: IfOperation): Expression {
        val guardAssumption = operation.guard.copy()
        val notGuardAssumption = OxstsFactory.createNegationOperator(operation.guard.copy())
        val bodyAssumption = visit(operation.body)
        val elseAssumption = if (operation.`else` != null) {
            visit(operation.`else`)
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

    override fun visit(operation: HavocOperation): Expression {
        return OxstsFactory.createLiteralBoolean(true)
    }

    override fun visit(operation: AssumptionOperation): Expression {
        return operation.expression.copy()
    }

    override fun visit(operation: AssignmentOperation): Expression {
        return OxstsFactory.createLiteralBoolean(true)
    }

    override fun visit(operation: InlineCall): Expression {
        return OxstsFactory.createLiteralBoolean(true)
    }

    override fun visit(operation: InlineIfOperation): Expression {
        return OxstsFactory.createLiteralBoolean(true)
    }

    override fun visit(operation: InlineSeqFor): Expression {
        return OxstsFactory.createLiteralBoolean(true)
    }

    override fun visit(operation: InlineChoiceFor): Expression {
        return OxstsFactory.createLiteralBoolean(true)
    }

}
