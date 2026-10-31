/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.theta.transformation.xsts

import com.google.inject.Inject
import hu.bme.mit.semantifyr.backend.BackendUnsupportedException
import hu.bme.mit.semantifyr.backend.transformation.BackendOperationVisitor
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AssignmentOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AssumptionOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ChoiceOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ForOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.HavocOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.IfOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LocalVarDeclarationOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Operation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.RangeExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.SequenceOperation

typealias XstsOperation = hu.bme.mit.semantifyr.xsts.lang.xsts.Operation

class ThetaOperationTransformer @Inject constructor(
    private val thetaExpressionTransformer: ThetaExpressionTransformer,
    private val thetaVariableTransformer: ThetaVariableTransformer,
) : BackendOperationVisitor<XstsOperation>() {

    fun transform(operation: Operation): XstsOperation {
        return visit(operation)
    }

    override fun visit(operation: SequenceOperation): XstsOperation {
        return XstsFactory.createSequenceOperation().also {
            for (step in operation.steps) {
                it.steps += transform(step)
            }
        }
    }

    override fun visit(operation: ChoiceOperation): XstsOperation {
        return XstsFactory.createChoiceOperation().also {
            for (branch in operation.branches) {
                it.branches += transform(branch)
            }
        }
    }

    override fun visit(operation: LocalVarDeclarationOperation): XstsOperation {
        return thetaVariableTransformer.transformLocalVar(operation)
    }

    override fun visit(operation: ForOperation): XstsOperation {
        val range = operation.rangeExpression as? RangeExpression
            ?: throw BackendUnsupportedException("Theta supports for-operations over ranges only")
        return XstsFactory.createForOperation().also {
            it.loopVar = thetaVariableTransformer.transform(operation.loopVariable)
            it.from = thetaExpressionTransformer.transform(range.left)
            it.to = transformRangeEnd(range)
            it.body = transform(operation.body)
        }
    }

    // XSTS ranges exclude their end
    private fun transformRangeEnd(range: RangeExpression): XstsExpression {
        val end = thetaExpressionTransformer.transform(range.right)
        if (range.isExclusive) {
            return end
        }
        return XstsFactory.createArithmeticBinaryOperator().also {
            it.left = end
            it.op = XstsArithmeticOp.ADD
            it.right = XstsFactory.createLiteralInteger().also { one ->
                one.value = 1
            }
        }
    }

    override fun visit(operation: IfOperation): XstsOperation {
        return XstsFactory.createIfOperation().also {
            it.guard = thetaExpressionTransformer.transform(operation.guard)
            it.body = transform(operation.body)
            if (operation.`else` != null) {
                it.`else` = transform(operation.`else`)
            }
        }
    }

    override fun visit(operation: HavocOperation): XstsOperation {
        return XstsFactory.createHavocOperation().also {
            it.reference = thetaExpressionTransformer.transformReference(operation.reference)
        }
    }

    override fun visit(operation: AssumptionOperation): XstsOperation {
        return XstsFactory.createAssumptionOperation().also {
            it.expression = thetaExpressionTransformer.transform(operation.expression)
        }
    }

    override fun visit(operation: AssignmentOperation): XstsOperation {
        return XstsFactory.createAssignmentOperation().also {
            it.reference = thetaExpressionTransformer.transformReference(operation.reference)
            it.expression = thetaExpressionTransformer.transform(operation.expression)
        }
    }
}
