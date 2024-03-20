package hu.bme.mit.gamma.oxsts.engine.transformation

import hu.bme.mit.gamma.oxsts.engine.utils.dropLast
import hu.bme.mit.gamma.oxsts.engine.utils.isDataType
import hu.bme.mit.gamma.oxsts.engine.utils.typedReferencedElement
import hu.bme.mit.gamma.oxsts.model.oxsts.ChainReferenceExpression
import hu.bme.mit.gamma.oxsts.model.oxsts.Containment
import hu.bme.mit.gamma.oxsts.model.oxsts.Feature
import hu.bme.mit.gamma.oxsts.model.oxsts.Reference
import hu.bme.mit.gamma.oxsts.model.oxsts.Type
import hu.bme.mit.gamma.oxsts.model.oxsts.Variable
import java.util.*

object Instantiator {

    fun instantiateTree(type: Type): Instance {
        val instanceQueue = LinkedList<Instance>()

        val rootContainment = OxstsFactory.createContainment().also {
            it.typing = OxstsFactory.createReferenceTyping(type)
            it.name = type.name
            it.multiplicity = OxstsFactory.createOneMultiplicity()
        }

        val rootInstance = Instance(rootContainment, null)
        instanceQueue += rootInstance

        while (instanceQueue.any()) {
            val instanceObject = instanceQueue.removeFirst()
            instanceObject.instantiateChildren()
            instanceQueue += instanceObject.children
        }

        // TODO: set reference bindings evaluates and adds all referenced instances to the reference hierarchy.
        //  Is this correct, or should we evaluate every time?
        setReferenceBindings(rootInstance)

        return rootInstance
    }

    private fun setReferenceBindings(rootInstance: Instance) {
        val instanceQueue = LinkedList<Instance>()

        instanceQueue += rootInstance.children

        while (instanceQueue.any()) {
            val instance = instanceQueue.removeFirst()
            instance.resolveReferenceBindings()
            instanceQueue += instance.children
        }
    }

    fun instantiateVariablesTree(rootInstance: Instance): List<Variable> {
        val instanceQueue = LinkedList<Instance>()

        instanceQueue += rootInstance
        val variables = mutableListOf<Variable>()

        while (instanceQueue.any()) {
            val instanceObject = instanceQueue.removeFirst()
            variables += instanceObject.instantiateVariables()
            instanceQueue += instanceObject.children
        }

        return variables
    }

    private fun Instance.resolveReferenceBindings() {
        for (feature in type.features.filterIsInstance<Reference>()) {
            if (!feature.isDataType && feature.expression != null) {
                place(feature, resolveBinding(feature))
            }
        }
    }

    private fun Instance.resolveBinding(feature: Feature): Set<Instance> = when (feature) {
        is Containment -> featureMap[feature] ?: emptySet()
        is Reference -> {
            if (feature.expression == null) { // not bound reference -> take actual contents
                featureMap[feature] ?: emptySet()
            } else {
                val chainingExpression = feature.expression as ChainReferenceExpression

                val context = expressionEvaluator.findFirstValidContext(chainingExpression)
                val holder = context.expressionEvaluator.evaluateInstanceReference(chainingExpression.dropLast(1))
                val referencedFeature = chainingExpression.typedReferencedElement<Feature>()

                holder.resolveBinding(referencedFeature) // recurse, until a free feature is found
            }
        }
        else -> error("Unsupported Feature type: $feature")
    }

}
