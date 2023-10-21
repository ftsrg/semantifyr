package hu.bme.mit.gamma.oxsts.engine.transformation

import hu.bme.mit.gamma.oxsts.engine.main
import hu.bme.mit.gamma.oxsts.model.oxsts.ChainReferenceExpression
import hu.bme.mit.gamma.oxsts.model.oxsts.ChainingExpression
import hu.bme.mit.gamma.oxsts.model.oxsts.DeclarationReferenceExpression
import hu.bme.mit.gamma.oxsts.model.oxsts.Feature
import hu.bme.mit.gamma.oxsts.model.oxsts.HavocTransitionExpression
import hu.bme.mit.gamma.oxsts.model.oxsts.InitTransitionExpression
import hu.bme.mit.gamma.oxsts.model.oxsts.Instance
import hu.bme.mit.gamma.oxsts.model.oxsts.MainTransitionExpression
import hu.bme.mit.gamma.oxsts.model.oxsts.ReferenceExpression
import hu.bme.mit.gamma.oxsts.model.oxsts.Target
import hu.bme.mit.gamma.oxsts.model.oxsts.Transition
import hu.bme.mit.gamma.oxsts.model.oxsts.Type

class TransitionEvaluator(
    private val instanceObject: InstanceObject
) {

    fun evaluateTransition(referenceExpression: ReferenceExpression): Transition {
        require(referenceExpression is ChainReferenceExpression)

        val type = getReferencedType(referenceExpression)

        val reference = referenceExpression.chains.last()

        val transition = type.findTransitionUpwards(reference)

        if (transition.isVirtual || transition.isOverride) {
            return instanceObject.type.findTransitionUpwards(reference)
        }

        return transition
    }

    fun getReferencedType(referenceExpression: ReferenceExpression): Type {
        require(referenceExpression is ChainReferenceExpression)

        return if (referenceExpression.chains.size > 1) {
            // feature based

            val feature = referenceExpression.chains.dropLast(1).last().element as? Feature
            feature?.type ?: error("")
        } else {
            instanceObject.type
        }
    }

    private fun Type.findTransitionUpwards(chain: ChainingExpression): Transition {
        val transition = getTransition(chain) ?: supertype?.findTransitionUpwards(chain)

        check(transition != null) {
            "Transition $chain could not be found in the type hierarchy!"
        }

        return transition
    }

    private fun Type.getTransition(chain: ChainingExpression): Transition? {
        return when (chain) {
            is HavocTransitionExpression -> havocTransition.singleOrNull()
            is InitTransitionExpression -> initTransition.singleOrNull()
            is MainTransitionExpression -> mainTransition.singleOrNull()
            is DeclarationReferenceExpression -> {
                val reference = chain.element as Transition

                transitions.firstOrNull {
                    it.name == reference.name
                }
            }

            else -> null
        }
    }

}
