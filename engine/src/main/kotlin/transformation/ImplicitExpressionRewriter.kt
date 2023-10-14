package hu.bme.mit.gamma.oxsts.engine.transformation

import hu.bme.mit.gamma.oxsts.model.oxsts.ChainReferenceExpression
import hu.bme.mit.gamma.oxsts.model.oxsts.ChainingExpression
import hu.bme.mit.gamma.oxsts.model.oxsts.DeclarationReferenceExpression
import hu.bme.mit.gamma.oxsts.model.oxsts.Element
import hu.bme.mit.gamma.oxsts.model.oxsts.Feature
import hu.bme.mit.gamma.oxsts.model.oxsts.HavocTransitionExpression
import hu.bme.mit.gamma.oxsts.model.oxsts.ImplicitTransitionExpression
import hu.bme.mit.gamma.oxsts.model.oxsts.InitTransitionExpression
import hu.bme.mit.gamma.oxsts.model.oxsts.MainTransitionExpression
import hu.bme.mit.gamma.oxsts.model.oxsts.Type
import org.eclipse.xtext.EcoreUtil2


// .havoc to transition in name
// .main to unnamed transition
// .init to transition in init
// all declaration references to chain reference

object ImplicitExpressionRewriter {

    fun rewriteExpressions(element: Element) {
        val expressions = EcoreUtil2.getAllContentsOfType(element, ImplicitTransitionExpression::class.java)

        for (expression in expressions) {
            val referredElement = expression.resolve()
            val declarationReferenceExpression = OxstsFactory.createChainingExpression(referredElement)
            EcoreUtil2.replace(expression, declarationReferenceExpression)
        }
    }

    private fun ImplicitTransitionExpression.resolve(): Element {
        val type = referencedType()

        return when (this) {
            is HavocTransitionExpression -> type.havocTransition.single()
            is InitTransitionExpression -> type.initTransition.single()
            is MainTransitionExpression -> type.mainTransition.single()
            else -> error("Unknown type of implicit transition: $this")
        }
    }

    private fun ChainingExpression.referencedType(): Type {
        val chainReferenceExpression = EcoreUtil2.getContainerOfType(this, ChainReferenceExpression::class.java)
        val myIndex = chainReferenceExpression.chains.indexOf(this)

        if (myIndex == 0) {
            return EcoreUtil2.getContainerOfType(this, Type::class.java)
        }

        val feature = chainReferenceExpression.chains[myIndex - 1].evaluateReference()

        check(feature is Feature) {
            "Expression must refer to a feature!"
        }

        return feature.type
    }

    private fun ChainingExpression.evaluateReference(): Element {
        require(this is DeclarationReferenceExpression) {
            "Expression must be DeclarationReferenceExpression"
        }

        return element
    }

}
