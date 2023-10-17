package hu.bme.mit.gamma.oxsts.engine.transformation

import hu.bme.mit.gamma.oxsts.model.oxsts.ChainReferenceExpression
import hu.bme.mit.gamma.oxsts.model.oxsts.ChainingExpression
import hu.bme.mit.gamma.oxsts.model.oxsts.DeclarationReferenceExpression
import hu.bme.mit.gamma.oxsts.model.oxsts.HavocTransitionExpression
import hu.bme.mit.gamma.oxsts.model.oxsts.InitTransitionExpression
import hu.bme.mit.gamma.oxsts.model.oxsts.Instance
import hu.bme.mit.gamma.oxsts.model.oxsts.MainTransitionExpression
import hu.bme.mit.gamma.oxsts.model.oxsts.ReferenceExpression
import hu.bme.mit.gamma.oxsts.model.oxsts.Target
import hu.bme.mit.gamma.oxsts.model.oxsts.Transition
import hu.bme.mit.gamma.oxsts.model.oxsts.Type

class TransitionEvaluator(
    val instanceObject: InstanceObject
) {



    fun evaluateTransition(referenceExpression: ReferenceExpression): Transition {
        require(referenceExpression is ChainReferenceExpression)

        val reference = referenceExpression.chains.last()

        val holder = instanceObject.instanceHolder

        val transition = when (holder) {
            is Target -> holder.findUpperMostTransition(reference)
            is Instance -> holder.type.findUpperMostTransition(reference)
            else -> error("Should not happen")
        }

        if (false) { // transition.isVirtual
            //return findLastOverride(transition)
        }

        return transition
    }

    private fun Target.findUpperMostTransition(chain: ChainingExpression): Transition {
        return getTransition(chain)
    }

    private fun Target.getTransition(chain: ChainingExpression): Transition {
        return when (chain) {
            is InitTransitionExpression -> init
            is MainTransitionExpression -> transition
            is HavocTransitionExpression -> error("Targets may not have havoc transitions!")
            else -> error("Targets may not have named transitions!")
        }
    }

    private fun Type.findUpperMostTransition(chain: ChainingExpression): Transition {
        val transition = getTransition(chain) ?: supertype?.findUpperMostTransition(chain)

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
            is DeclarationReferenceExpression -> chain.element as? Transition
            else -> null
        }
    }

    fun findLastOverride(transition: Transition): Transition {
        return transition // TODO
    }

}
