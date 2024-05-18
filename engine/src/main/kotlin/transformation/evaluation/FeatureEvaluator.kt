package hu.bme.mit.semantifyr.oxsts.engine.transformation.evaluation

import hu.bme.mit.semantifyr.oxsts.engine.transformation.resolution.RedefinitionHandler
import hu.bme.mit.semantifyr.oxsts.engine.utils.NothingInstance
import hu.bme.mit.semantifyr.oxsts.engine.utils.contextualEvaluator
import hu.bme.mit.semantifyr.oxsts.engine.utils.dropLast
import hu.bme.mit.semantifyr.oxsts.engine.utils.featureEvaluator
import hu.bme.mit.semantifyr.oxsts.engine.utils.instancePlacer
import hu.bme.mit.semantifyr.oxsts.engine.utils.isDataType
import hu.bme.mit.semantifyr.oxsts.engine.utils.lastChain
import hu.bme.mit.semantifyr.oxsts.engine.utils.referencedElement
import hu.bme.mit.semantifyr.oxsts.engine.utils.type
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ChainReferenceExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ChainingExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.DeclarationReferenceExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Feature
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Instance
import hu.bme.mit.semantifyr.oxsts.model.oxsts.NothingReference
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Reference
import hu.bme.mit.semantifyr.oxsts.model.oxsts.SelfReference

class FeatureEvaluator(
    private val context: Instance
) {

    fun evaluate(expression: ChainReferenceExpression): DataType {
        if (expression.chains.size == 0) {
            // this is a Self expression, return context
            return InstanceData(setOf(context))
        }

        val context = evaluateInstance(expression.dropLast(1))

        return context.featureEvaluator.evaluate(expression.lastChain())
    }

    private fun evaluate(expression: ChainingExpression): DataType {
        return when (expression) {
            is NothingReference -> InstanceData(setOf(NothingInstance))
            is SelfReference -> InstanceData(setOf(context))
            is DeclarationReferenceExpression -> {
                val feature = expression.referencedElement() as Feature
                val actualFeature = RedefinitionHandler.resolveFeature(context.type, feature)

                if (actualFeature.isDataType) {
                    check(actualFeature is Reference)

                    context.contextualEvaluator.evaluate(actualFeature.expression)
                } else {
                    val instanceSet = context.featureEvaluator.evaluateInstanceSet(expression)

                    InstanceData(instanceSet)
                }
            }
            else -> error("Expression $this must be a DeclarationReferenceExpression!")
        }
    }

    private fun evaluateInstance(reference: ChainReferenceExpression): Instance {
        return evaluateInstanceOrNull(reference) ?: error("Reference points to empty feature or Nothing literal!")
    }

    private fun evaluateInstanceOrNull(reference: ChainReferenceExpression): Instance? {
        val instanceSet = evaluateInstanceSet(reference)

        if (instanceSet.size > 1) {
            error("Chain refers to a non-singular feature!")
        }

        return instanceSet.singleOrNull()
    }

    private fun evaluateInstanceSet(reference: ChainReferenceExpression): Set<Instance> {
        var localContext = setOf(context)

        for (chain in reference.chains) {
            check(localContext.size == 1) {
                "Feature for $chain has ${localContext.size} elements!"
            }

            localContext = localContext.single().featureEvaluator.evaluateInstanceSet(chain)
        }

        return localContext
    }

    private fun evaluateInstanceSet(expression: ChainingExpression): Set<Instance> {
        return when (expression) {
            is NothingReference -> setOf(NothingInstance)
            is SelfReference -> setOf(context)
            is DeclarationReferenceExpression -> evaluateInstanceSet(expression)
            else -> error("Expression $this must be a DeclarationReferenceExpression!")
        }
    }

    private fun evaluateInstanceSet(expression: DeclarationReferenceExpression): Set<Instance> {
        require(expression.element is Feature) {
            error("Expression $expression must refer to a feature!")
        }

        val actualFeature = RedefinitionHandler.resolveFeature(context.type, expression.element as Feature)

        return context.instancePlacer[actualFeature]
    }

}
