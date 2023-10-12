package hu.bme.mit.gamma.oxsts.engine.transformation

import hu.bme.mit.gamma.oxsts.model.oxsts.Feature
import hu.bme.mit.gamma.oxsts.model.oxsts.Instance
import hu.bme.mit.gamma.oxsts.model.oxsts.Target
import hu.bme.mit.gamma.oxsts.model.oxsts.Type
import java.util.*

open class InstanceObject(
    val instance: Instance?
) {
    val featureMap = mutableMapOf<Feature, InstanceObject>()

    val expressionEvaluator = ExpressionEvaluator(this)
    val operationEvaluator = InlineOperationEvaluator(this)

    val allInstances: List<Instance>
        get() = instance?.allInstances ?: emptyList()

    // derived (inherited) things?
}

val Instance.allInstances: List<Instance>
    get() {
        val list = mutableListOf<Instance>()
        list.addAll(instances)
        list.addAll(type.allInstances)
        return list
    }

val Type.allInstances: List<Instance>
    get() {
        val list = mutableListOf<Instance>()
        list.addAll(features.filterIsInstance<Instance>())
        if (supertype != null) {
            list.addAll(supertype.allInstances)
        }
        return list
    }

object NothingInstance : InstanceObject(null)

class Instantiator {
    val instanceQueue = LinkedList<InstanceObject>()

    fun instantiate(target: Target): InstanceObject {
        val rootInstanceObject = InstanceObject(null)

        rootInstanceObject.instanciateInstances(target.instances)

        while (instanceQueue.any()) {
            val next = instanceQueue.removeFirst()

            next.instanciateInstances(next.allInstances)
        }

        return rootInstanceObject
    }

    fun InstanceObject.instanciateInstances(instances: List<Instance>) {
        for (instance in instances) {
            val instanceObject = instance.createInstanceObject()
            place(instance, instanceObject)
        }
    }

    fun Instance.createInstanceObject(): InstanceObject {
        val instanceObject = InstanceObject(this)
        instanceQueue.add(instanceObject)
        return instanceObject
    }

    fun InstanceObject.place(feature: Feature, instanceObject: InstanceObject) {
        featureMap[feature] = instanceObject
        if (feature.subsets != null) {
            place(feature.subsets, instanceObject)
        }
    }

}
