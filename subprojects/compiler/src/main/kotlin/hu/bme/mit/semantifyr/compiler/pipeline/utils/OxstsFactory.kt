/*
 * SPDX-FileCopyrightText: 2023-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.utils

import hu.bme.mit.semantifyr.oxsts.model.oxsts.AssumptionOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.BooleanOp
import hu.bme.mit.semantifyr.oxsts.model.oxsts.BooleanOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ElementReference
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralBoolean
import hu.bme.mit.semantifyr.oxsts.model.oxsts.NamedElement
import hu.bme.mit.semantifyr.oxsts.model.oxsts.NegationOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.UnaryOp
import hu.bme.mit.semantifyr.oxsts.model.oxsts.impl.OxstsFactoryImpl

/**
 * Shared [OxstsFactoryImpl] singleton with helper functions.
 */
object OxstsFactory : OxstsFactoryImpl() {

    fun createBooleanOperator(op: BooleanOp, left: Expression, right: Expression): BooleanOperator {
        return createBooleanOperator().also {
            it.op = op
            it.left = left
            it.right = right
        }
    }

    fun createAssumptionOperation(value: Boolean): AssumptionOperation {
        return createAssumptionOperation(createLiteralBoolean(value))
    }

    fun createAssumptionOperation(expression: Expression): AssumptionOperation {
        return createAssumptionOperation().also {
            it.expression = expression
        }
    }

    fun createNegationOperator(expression: Expression): NegationOperator {
        return createNegationOperator().also {
            it.body = expression
        }
    }

    fun createLiteralBoolean(value: Boolean): LiteralBoolean {
        return createLiteralBoolean().also {
            it.isValue = value
        }
    }

    fun createLiteralInteger(value: Int): Expression {
        if (value >= 0) {
            return createLiteralInteger().also {
                it.value = value
            }
        }

        return createArithmeticUnaryOperator().also {
            it.op = UnaryOp.MINUS
            it.body = createLiteralInteger().also {
                it.value = -value
            }
        }
    }

    fun createElementReference(element: NamedElement): ElementReference {
        return createElementReference().also {
            it.element = element
        }
    }

}
