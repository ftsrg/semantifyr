package hu.bme.mit.gamma.oxsts.engine.transformation

import hu.bme.mit.gamma.oxsts.model.oxsts.AndOperator
import hu.bme.mit.gamma.oxsts.model.oxsts.Association
import hu.bme.mit.gamma.oxsts.model.oxsts.AssumptionOperation
import hu.bme.mit.gamma.oxsts.model.oxsts.ChainReferenceExpression
import hu.bme.mit.gamma.oxsts.model.oxsts.ChainingExpression
import hu.bme.mit.gamma.oxsts.model.oxsts.Containment
import hu.bme.mit.gamma.oxsts.model.oxsts.DeclarationReferenceExpression
import hu.bme.mit.gamma.oxsts.model.oxsts.Element
import hu.bme.mit.gamma.oxsts.model.oxsts.EnumLiteral
import hu.bme.mit.gamma.oxsts.model.oxsts.Expression
import hu.bme.mit.gamma.oxsts.model.oxsts.Feature
import hu.bme.mit.gamma.oxsts.model.oxsts.InlineCall
import hu.bme.mit.gamma.oxsts.model.oxsts.Instance
import hu.bme.mit.gamma.oxsts.model.oxsts.LiteralBoolean
import hu.bme.mit.gamma.oxsts.model.oxsts.LiteralInteger
import hu.bme.mit.gamma.oxsts.model.oxsts.NotOperator
import hu.bme.mit.gamma.oxsts.model.oxsts.Operation
import hu.bme.mit.gamma.oxsts.model.oxsts.OrOperator
import hu.bme.mit.gamma.oxsts.model.oxsts.Package
import hu.bme.mit.gamma.oxsts.model.oxsts.ReferenceExpression
import hu.bme.mit.gamma.oxsts.model.oxsts.ReferenceTyping
import hu.bme.mit.gamma.oxsts.model.oxsts.impl.OxstsFactoryImpl
import org.eclipse.emf.ecore.EObject

object OxstsFactory : OxstsFactoryImpl() {
    fun createEmptyOperation(): Operation {
        return createAssumptionOperation(createLiteralBoolean(true))
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

    fun createInlineCall(referenceExpression: ReferenceExpression): InlineCall {
        return createInlineCall().also {
            it.reference = referenceExpression
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

}

val Element._package
    get() = if (this is Package) this else eContainer()._package

val EObject._package: Package
    get() = if (this is Package) this else eContainer()._package
