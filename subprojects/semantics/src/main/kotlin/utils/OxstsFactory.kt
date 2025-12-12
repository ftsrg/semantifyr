/*
 * SPDX-FileCopyrightText: 2023-2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.semantics.utils

import hu.bme.mit.semantifyr.oxsts.model.oxsts.Association
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AssumptionOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.BooleanOp
import hu.bme.mit.semantifyr.oxsts.model.oxsts.BooleanOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.DomainDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ElementReference
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.FeatureDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Instance
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralBoolean
import hu.bme.mit.semantifyr.oxsts.model.oxsts.NamedElement
import hu.bme.mit.semantifyr.oxsts.model.oxsts.NegationOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.UnaryOp
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableMapping
import hu.bme.mit.semantifyr.oxsts.model.oxsts.impl.OxstsFactoryImpl

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

    fun createInstance(domainDeclaration: DomainDeclaration): Instance {
        return createInstance().also {
            it.domain = domainDeclaration
        }
    }

    fun createAssociation(feature: FeatureDeclaration): Association {
        return createAssociation().also {
            it.feature = feature
        }
    }
    fun createVariableMapping(variableDeclaration: VariableDeclaration): VariableMapping {
        return createVariableMapping().also {
            it.original = variableDeclaration
            it.actual = variableDeclaration.copy()
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
