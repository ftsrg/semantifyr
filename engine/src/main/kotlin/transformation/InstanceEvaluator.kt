package hu.bme.mit.gamma.oxsts.engine.transformation

import hu.bme.mit.gamma.oxsts.model.oxsts.*

class InstanceEvaluator(
    private val context: Instance
) {

    fun evaluateInstanceSet(reference: ReferenceExpression): List<Instance> {
        require(reference is ChainReferenceExpression) {
            "Expression $this must be ChainReferenceExpression"
        }

        var localContext = listOf(context)
        for (chain in reference.chains) {
            check(localContext.size == 1) {
                "Feature for $chain has ${localContext.size} elements!"
            }

            with(localContext.single().instanceEvaluator) {
                localContext = chain.evaluateInstanceSet()
            }
        }
        return localContext
    }

    private fun ChainingExpression.evaluateInstanceSet(): List<Instance> {
        return when (this) {
            is NothingReference -> listOf(NothingInstance)
            is SelfReference -> listOf(context)
            is DeclarationReferenceExpression -> evaluateInstanceSet()
            else -> error("Expression $this must be a DeclarationReferenceExpression!")
        }
    }

    private fun DeclarationReferenceExpression.evaluateInstanceSet(): List<Instance> {
        require(element is Feature) {
            error("Expression $this must refer to a feature!")
        }

        val actualFeature = RedefinitionHandler.resolveFeature(context.type, element as Feature)

        return context.featureMap[actualFeature] ?: emptyList()// error("No instance for feature $element!")
    }

}
