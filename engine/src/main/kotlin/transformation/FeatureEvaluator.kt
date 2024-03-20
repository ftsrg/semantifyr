package hu.bme.mit.gamma.oxsts.engine.transformation

import hu.bme.mit.gamma.oxsts.model.oxsts.*

class FeatureEvaluator(
    private val context: Instance
) {

    fun evaluateInstanceSet(reference: ReferenceExpression): Set<Instance> {
        require(reference is ChainReferenceExpression) {
            "Expression $this must be ChainReferenceExpression"
        }

        var localContext = setOf(context)
        for (chain in reference.chains) {
            check(localContext.size == 1) {
                "Feature for $chain has ${localContext.size} elements!"
            }

            with(localContext.single().featureEvaluator) {
                localContext = chain.evaluateInstanceSet()
            }
        }
        return localContext
    }

    private fun ChainingExpression.evaluateInstanceSet(): Set<Instance> {
        return when (this) {
            is NothingReference -> setOf(NothingInstance)
            is SelfReference -> setOf(context)
            is DeclarationReferenceExpression -> evaluateInstanceSet()
            else -> error("Expression $this must be a DeclarationReferenceExpression!")
        }
    }

    private fun DeclarationReferenceExpression.evaluateInstanceSet(): Set<Instance> {
        // TODO: this method should return DataType in a generic way (i.e. if feature is datatype, then return the evaluated expression)
        require(element is Feature) {
            error("Expression $this must refer to a feature!")
        }

        val actualFeature = RedefinitionHandler.resolveFeature(context.type, element as Feature)

        return context.featureMap[actualFeature] ?: emptySet()// error("No instance for feature $element!")
    }

}
