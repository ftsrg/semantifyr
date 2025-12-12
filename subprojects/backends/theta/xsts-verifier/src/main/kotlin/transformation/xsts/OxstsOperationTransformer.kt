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
import hu.bme.mit.semantifyr.oxsts.model.oxsts.RangeExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.SequenceOperation

private typealias XstsOperation = hu.bme.mit.semantifyr.xsts.lang.xsts.Operation

class OxstsOperationTransformer : OperationVisitor<XstsOperation>() {

    @Inject
    private lateinit var oxstsExpressionTransformer: OxstsExpressionTransformer

    @Inject
    private lateinit var oxstsVariableTransformer: OxstsVariableTransformer

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
        return oxstsVariableTransformer.transformLocalVar(operation)
    }

    override fun visit(operation: ForOperation): XstsOperation {
        return XstsFactory.createForOperation().also {
            it.loopVar = oxstsVariableTransformer.transform(operation.loopVariable)
            val range = operation.rangeExpression
            if (range is RangeExpression) {
                // TODO: handle inclusive-exclusive!
                it.from = oxstsExpressionTransformer.transform(range.left)
                it.to = oxstsExpressionTransformer.transform(range.right)
            }
            it.body = transform(operation.body)
        }
    }

    override fun visit(operation: IfOperation): XstsOperation {
        return XstsFactory.createIfOperation().also {
            it.guard = oxstsExpressionTransformer.transform(operation.guard)
            it.body = transform(operation.body)

            if (operation.`else` != null) {
                it.`else` = transform(operation.`else`)
            }
        }
    }

    override fun visit(operation: HavocOperation): XstsOperation {
        return XstsFactory.createHavocOperation().also {
            it.reference = oxstsExpressionTransformer.transformReference(operation.reference)
        }
    }

    override fun visit(operation: AssumptionOperation): XstsOperation {
        return XstsFactory.createAssumptionOperation().also {
            it.expression = oxstsExpressionTransformer.transform(operation.expression)
        }
    }

    override fun visit(operation: AssignmentOperation): XstsOperation {
        return XstsFactory.createAssignmentOperation().also {
            it.reference = oxstsExpressionTransformer.transformReference(operation.reference)
            it.expression = oxstsExpressionTransformer.transform(operation.expression)
        }
    }

    override fun visit(operation: InlineCall): XstsOperation {
        error("No equivalent in XSTS!")
    }

    override fun visit(operation: InlineIfOperation): XstsOperation {
        error("No equivalent in XSTS!")
    }

    override fun visit(operation: InlineSeqFor): XstsOperation {
        error("No equivalent in XSTS!")
    }

    override fun visit(operation: InlineChoiceFor): XstsOperation {
        error("No equivalent in XSTS!")
    }

}
