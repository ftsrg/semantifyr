package hu.bme.mit.gamma.oxsts.engine.transformation

import hu.bme.mit.gamma.oxsts.model.oxsts.Feature
import hu.bme.mit.gamma.oxsts.model.oxsts.Instance
import hu.bme.mit.gamma.oxsts.model.oxsts.Target
import hu.bme.mit.gamma.oxsts.model.oxsts.Type
import java.util.*

open class InstanceObject(
    val instance: Instance?,
    val parent: InstanceObject?,
) {
    val featureMap = mutableMapOf<Feature, MutableList<InstanceObject>>()

    fun addInstanceObject(feature: Feature, instanceObject: InstanceObject) {
        val list = featureMap.computeIfAbsent(feature) {
            mutableListOf()
        }
        list.add(instanceObject)
    }

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

object NothingInstance : InstanceObject(null, null)

class Instantiator {
    val instanceQueue = LinkedList<InstanceObject>()

    fun instantiate(target: Target): InstanceObject {
        val rootInstanceObject = InstanceObject(null, null)

        rootInstanceObject.instanciateInstances(target.instances)

        while (instanceQueue.any()) {
            val next = instanceQueue.removeFirst()

            next.instanciateInstances(next.allInstances)
        }

        return rootInstanceObject
    }

    fun InstanceObject.instanciateInstances(instances: List<Instance>) {
        for (instance in instances) {
            val instanceObject = InstanceObject(instance, this)
            instanceQueue.add(instanceObject)
            place(instance, instanceObject)
        }
    }

    fun InstanceObject.place(feature: Feature, instanceObject: InstanceObject) {
        addInstanceObject(feature, instanceObject)
        if (feature.subsets != null) {
            place(feature.subsets, instanceObject)
        }
    }

}
