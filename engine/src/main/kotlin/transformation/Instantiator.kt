package hu.bme.mit.gamma.oxsts.engine.transformation

import hu.bme.mit.gamma.oxsts.engine.utils.asChainReferenceExpression
import hu.bme.mit.gamma.oxsts.engine.utils.dropLast
import hu.bme.mit.gamma.oxsts.model.oxsts.Feature
import hu.bme.mit.gamma.oxsts.model.oxsts.Instance
import hu.bme.mit.gamma.oxsts.model.oxsts.Target
import hu.bme.mit.gamma.oxsts.model.oxsts.Variable
import java.util.*

object Instantiator {

    fun instantiateInstances(target: Target): InstanceObject {
        val instanceQueue = LinkedList<InstanceObject>()

        val rootInstanceObject = InstanceObject(target, null)
        instanceQueue += rootInstanceObject

        while (instanceQueue.any()) {
            val instanceObject = instanceQueue.removeFirst()
            instanceObject.instantiateChildren()
            instanceQueue += instanceObject.children
        }

        setReferenceBindings(rootInstanceObject)

        return rootInstanceObject
    }

    fun instantiateVariables(rootInstanceObject: InstanceObject): List<Variable> {
        val instanceQueue = LinkedList<InstanceObject>()

        instanceQueue += rootInstanceObject
        val variables = mutableListOf<Variable>()

        while (instanceQueue.any()) {
            val instanceObject = instanceQueue.removeFirst()
            variables += instanceObject.instantiateVariables()
            instanceQueue += instanceObject.children
        }

        return variables
    }

    private fun setReferenceBindings(rootInstanceObject: InstanceObject) {
        val instanceQueue = LinkedList<InstanceObject>()

        instanceQueue += rootInstanceObject.children

        while (instanceQueue.any()) {
            val instanceObject = instanceQueue.removeFirst()
            instanceObject.placeReferences()
            instanceQueue += instanceObject.children
        }
    }

    private fun InstanceObject.placeReferences() {
        if (instanceHolder !is Instance) {
            return
        }

        for (binding in instanceHolder.bindings) {
            val holder = expressionEvaluator.evaluateInstanceObject(binding.feature.asChainReferenceExpression().dropLast(1))
            val feature = expressionEvaluator.evaluateTypedReference<Feature>(binding.feature.asChainReferenceExpression())
            val held = expressionEvaluator.evaluateInstanceObjectBottomUp(binding.instance)

            holder.place(feature, held)
        }
    }

}
