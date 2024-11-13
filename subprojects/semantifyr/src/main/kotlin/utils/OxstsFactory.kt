/*
 * SPDX-FileCopyrightText: 2023-2024 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.semantifyr.utils

import hu.bme.mit.semantifyr.oxsts.model.oxsts.AndOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AssignmentOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Association
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AssumptionOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ChainReferenceExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ChainingExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Containment
import hu.bme.mit.semantifyr.oxsts.model.oxsts.DeclarationReferenceExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Element
import hu.bme.mit.semantifyr.oxsts.model.oxsts.EnumLiteral
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Feature
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlineCall
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Instance
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralBoolean
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralInteger
import hu.bme.mit.semantifyr.oxsts.model.oxsts.NotOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Operation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.OrOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ReferenceExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ReferenceTyping
import hu.bme.mit.semantifyr.oxsts.model.oxsts.SequenceOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Transition
import hu.bme.mit.semantifyr.oxsts.model.oxsts.impl.OxstsFactoryImpl

object OxstsFactory : OxstsFactoryImpl() {
    fun createEmptyOperation(): Operation {
        return createAssumptionOperation(createLiteralBoolean(true))
    }

    inline fun createSequentialTransition(creator: SequenceOperation.() -> Unit): Transition {
        return createTransition().also {
            it.operation += createSequenceOperation().also(creator)
        }
    }

    fun createEnumLiteral(name: String): EnumLiteral {
        return createEnumLiteral().also {
            it.name = name
        }
    }

    fun createReferenceTyping(element: Element): ReferenceTyping {
        return createReferenceTyping().also {
            it.reference = createChainReferenceExpression(element)
        }
    }

    fun createInstance(containment: Containment): Instance {
        return createInstance().also {
            it.containment = containment
        }
    }

    fun createInstance(containment: Containment, parent: Instance): Instance {
        return createInstance(containment).also {
            it.parent = parent
        }
    }

    fun createAssociation(feature: Feature): Association {
        return createAssociation().also {
            it.feature = feature
        }
    }

    fun createDeclarationReferenceExpression(element: Element): DeclarationReferenceExpression {
        return createDeclarationReference(element) as DeclarationReferenceExpression
    }

    fun createDeclarationReference(element: Element): ChainingExpression {
        return createDeclarationReferenceExpression().apply {
            this.element = element
        }
    }

    fun createChainReferenceExpression(element: Element): ChainReferenceExpression {
        return createChainReferenceExpression().apply {
            chains += createDeclarationReference(element)
        }
    }

    fun createChainReferenceExpression(chains: List<ChainingExpression>): ChainReferenceExpression {
        return OxstsFactory.createChainReferenceExpression().also {
            it.chains += chains
        }
    }

    fun createChainReferenceExpression(expression: ChainingExpression): ChainReferenceExpression {
        return createChainReferenceExpression().apply {
            chains += expression
        }
    }

    fun createInlineCall(referenceExpression: ReferenceExpression, isStatic: Boolean = false): InlineCall {
        return createInlineCall().also {
            it.reference = referenceExpression
            it.isStatic = isStatic
        }
    }

    fun createAssumptionOperation(expression: Expression): AssumptionOperation {
        return createAssumptionOperation().also {
            it.expression = expression
        }
    }

    fun createLiteralBoolean(value: Boolean): LiteralBoolean {
        return createLiteralBoolean().also {
            it.isValue = value
        }
    }

    fun createLiteralInteger(value: Int): LiteralInteger {
        return createLiteralInteger().also {
            it.value = value
        }
    }

    fun createAndOperator(lhs: Expression, rhs: Expression): AndOperator {
        return createAndOperator().also {
            it.operands += lhs
            it.operands += rhs
        }
    }

    fun createOrOperator(lhs: Expression, rhs: Expression): OrOperator {
        return createOrOperator().also {
            it.operands += lhs
            it.operands += rhs
        }
    }

    fun createNotOperator(expression: Expression): NotOperator {
        return createNotOperator().also {
            it.operands += expression
        }
    }

    fun createEqualityAssumption(referenceExpression: ReferenceExpression, expression: Expression): AssumptionOperation {
        return createAssumptionOperation(
            createEqualityOperator().also {
                it.operands += referenceExpression
                it.operands += expression
            }
        )
    }

    fun createAssignmentOperation(referenceExpression: ReferenceExpression, expression: Expression): AssignmentOperation {
        return createAssignmentOperation().also {
            it.reference = referenceExpression
            it.expression = expression
        }
    }

}
