/*
 * SPDX-FileCopyrightText: 2023-2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.semantifyr.transformation.rewrite

import hu.bme.mit.semantifyr.oxsts.model.oxsts.AssignmentOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AssumptionOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ChoiceOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.CompositeOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.HavocOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.IfOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Operation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.SequenceOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Transition
import hu.bme.mit.semantifyr.oxsts.semantifyr.transformation.optimization.OperationOptimizer.optimize
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.OxstsFactory
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.copy

object ChoiceElseRewriter {

    fun Transition.rewriteChoiceElse() {
        for (op in operation) {
            op.rewriteChoiceElse()
        }
    }

    private fun Operation.rewriteChoiceElse() {
        when (this) {
            is ChoiceOperation -> {
                for (op in operation) {
                    op.rewriteChoiceElse()
                }

                if (`else` != null) {
                    `else`.rewriteChoiceElse()
                }
            }

            is IfOperation -> {
                body.rewriteChoiceElse()

                if (`else` != null) {
                    `else`.rewriteChoiceElse()
                }
            }

            is CompositeOperation -> {
                for (op in operation) {
                    op.rewriteChoiceElse()
                }
            }
        }

        if (this !is ChoiceOperation) return
        if (`else` == null) return

        val assumption = calculateAssumption()
        val notOperator = OxstsFactory.createNotOperator(assumption)
        val notAssumption = OxstsFactory.createAssumptionOperation(notOperator)
        notAssumption.optimize()
        val branch = OxstsFactory.createSequenceOperation().also {
            it.operation += notAssumption
            if (`else` != null) {
                it.operation += `else`
            }
        }

        operation += branch
    }

    private fun Operation.calculateAssumption(): Expression {
        return when (this) {
            is AssumptionOperation -> expression.copy()
            is AssignmentOperation -> OxstsFactory.createLiteralBoolean(true)
            is HavocOperation -> OxstsFactory.createLiteralBoolean(true)
            is SequenceOperation -> {
                // all branches can be executed
                operation.map {
                    it.calculateAssumption()
                }.reduceOrNull { lhs, rhs ->
                    OxstsFactory.createAndOperator(lhs, rhs)
                } ?: OxstsFactory.createLiteralBoolean(true)
            }

            is ChoiceOperation -> {
                // any branch can be executed
                operation.map {
                    it.calculateAssumption()
                }.reduceOrNull { lhs, rhs ->
                    OxstsFactory.createOrOperator(lhs, rhs)
                } ?: OxstsFactory.createLiteralBoolean(true)
            }

            is IfOperation -> {
                val guardAssumption = guard.copy()
                val notGuardAssumption = OxstsFactory.createNotOperator(guard.copy())
                val bodyAssumption = body.calculateAssumption()
                val elseAssumption = `else`?.calculateAssumption() ?: OxstsFactory.createLiteralBoolean(true)

                // if can be executed, if the guard is true and the body can be executed,
                //  or the guard is false and the else can be executed
                OxstsFactory.createOrOperator(
                    OxstsFactory.createAndOperator(guardAssumption, bodyAssumption),
                    OxstsFactory.createAndOperator(notGuardAssumption, elseAssumption),
                )
            }

            else -> error("Unknown operation: $this!")
        }
    }

}
