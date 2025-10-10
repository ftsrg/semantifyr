/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.semantics.optimization

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
import hu.bme.mit.semantifyr.semantics.utils.isConstantLiteralTrue

class IsAlwaysExecutableEvaluator : OperationVisitor<Boolean>() {

    fun isAlwaysExecutable(operation: Operation): Boolean {
        return visit(operation)
    }

    override fun visit(operation: SequenceOperation): Boolean {
        return operation.steps.all {
            visit(it)
        }
    }

    override fun visit(operation: ChoiceOperation): Boolean {
        return operation.branches.any {
            visit(it)
        }
    }

    override fun visit(operation: LocalVarDeclarationOperation): Boolean {
        return true
    }

    override fun visit(operation: ForOperation): Boolean {
        return visit(operation.body)
    }

    override fun visit(operation: IfOperation): Boolean {
        return visit(operation.body)
            && operation.`else`?.let {
                visit(it)
            } ?: true
    }

    override fun visit(operation: HavocOperation): Boolean {
        return true
    }

    override fun visit(operation: AssumptionOperation): Boolean {
        return operation.expression.isConstantLiteralTrue
    }

    override fun visit(operation: AssignmentOperation): Boolean {
        return true
    }

    override fun visit(operation: InlineCall): Boolean {
        return true
    }

    override fun visit(operation: InlineIfOperation): Boolean {
        return true
    }

    override fun visit(operation: InlineSeqFor): Boolean {
        return true
    }

    override fun visit(operation: InlineChoiceFor): Boolean {
        return true
    }

}
