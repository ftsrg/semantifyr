package hu.bme.mit.gamma.oxsts.engine.transformation

import hu.bme.mit.gamma.oxsts.model.oxsts.Feature
import hu.bme.mit.gamma.oxsts.model.oxsts.Instance
import hu.bme.mit.gamma.oxsts.model.oxsts.Target
import hu.bme.mit.gamma.oxsts.model.oxsts.Type
import hu.bme.mit.gamma.oxsts.model.oxsts.Variable
import java.util.*

open class InstanceObject(
    val instance: Instance?,
    val parent: InstanceObject?,
) {
    val featureMap = mutableMapOf<Feature, MutableList<InstanceObject>>()
    val variableMap = mutableMapOf<Variable, Variable>()

    fun addInstanceObject(feature: Feature, instanceObject: InstanceObject) {
        val list = featureMap.computeIfAbsent(feature) {
            mutableListOf()
        }
        list += instanceObject
    }

    val expressionEvaluator = ExpressionEvaluator(this)
    val operationEvaluator = InlineOperationEvaluator(this)

    val allInstances: List<Instance>
        get() = instance?.allInstances ?: emptyList()

    val allVariables: List<Variable>
        get() = instance?.allVariables ?: emptyList()


    fun flattenVariables(): List<Variable> {
        val variables = allVariables

        for (variable in variables) {
            val newVariable = variable.copy()
            newVariable.name = "${fullyQualifiedName}__${variable.name}"
            variableMap[variable] = newVariable
        }

        return variableMap.values.toList()
    }

    val name by lazy {
        instance?.name ?: "root"
    }

    val fullyQualifiedName: String by lazy {
        val parentName = parent?.fullyQualifiedName ?: ""

        "${parentName}__${name}"
    }
}

val Instance.allInstances: List<Instance>
    get() {
        val list = mutableListOf<Instance>()
        list += instances
        list += type.allInstances
        return list
    }

val Instance.allVariables: List<Variable>
    get() = type.allVariables

val Type.allInstances: List<Instance>
    get() {
        val list = mutableListOf<Instance>()
        list += features.filterIsInstance<Instance>()
        if (supertype != null) {
            list += supertype.allInstances
        }
        return list
    }

val Type.allVariables: List<Variable>
    get() {
        val list = mutableListOf<Variable>()
        list += variables
        if (supertype != null) {
            list += supertype.allVariables
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

            target.variables += next.flattenVariables()
        }

        return rootInstanceObject
    }

    fun InstanceObject.instanciateInstances(instances: List<Instance>) {
        for (instance in instances) {
            val instanceObject = InstanceObject(instance, this)
            instanceQueue += instanceObject
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
