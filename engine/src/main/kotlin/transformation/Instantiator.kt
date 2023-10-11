package hu.bme.mit.gamma.oxsts.engine.transformation

import hu.bme.mit.gamma.oxsts.model.oxsts.Instance
import hu.bme.mit.gamma.oxsts.model.oxsts.Target
import java.util.*

class InstanceObject(
    val instance: Instance?
) {
    val instanceMap = mutableMapOf<Instance, InstanceObject>()

    // derived (inherited) things?
}

class Instantiator {
    val instanceQueue = LinkedList<InstanceObject>()

    fun instantiate(target: Target): InstanceObject {
        val rootInstanceObject = InstanceObject(null)

        rootInstanceObject.instanciateInstances(target.instances)

        while (instanceQueue.any()) {
            val next = instanceQueue.removeFirst()

            next.instanciateInstances(next.instance!!.instances)
        }

        return rootInstanceObject
    }

    fun InstanceObject.instanciateInstances(instances: List<Instance>) {
        for (instance in instances) {
            instanceMap[instance] = instance.createInstanceObject()
        }
    }

    fun Instance.createInstanceObject(): InstanceObject {
        val instanceObject = InstanceObject(this)
        instanceQueue.add(instanceObject)
        return instanceObject
    }

}
