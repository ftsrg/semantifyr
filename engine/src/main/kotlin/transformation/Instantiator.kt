package hu.bme.mit.gamma.oxsts.engine.transformation

import hu.bme.mit.gamma.oxsts.model.oxsts.Enum
import hu.bme.mit.gamma.oxsts.model.oxsts.Feature
import hu.bme.mit.gamma.oxsts.model.oxsts.Instance
import hu.bme.mit.gamma.oxsts.model.oxsts.InstanceHolder
import hu.bme.mit.gamma.oxsts.model.oxsts.Target
import hu.bme.mit.gamma.oxsts.model.oxsts.Type
import hu.bme.mit.gamma.oxsts.model.oxsts.Variable
import org.eclipse.xtext.EcoreUtil2
import java.util.*

open class InstanceObject(
    val instanceHolder: InstanceHolder?,
    val parent: InstanceObject?,
) {
    val featureMap = mutableMapOf<Feature, MutableList<InstanceObject>>()
    val variableMap = mutableMapOf<Variable, Variable>()
    val featureEnumMap = mutableMapOf<Feature, EnumMapping>()

    val expressionEvaluator = ExpressionEvaluator(this)
    val operationEvaluator = InlineOperationEvaluator(this)
    val transitionEvaluator = TransitionEvaluator(this)
    val variableTransformer = VariableTransformer(this)

    val allInstances = instanceHolder?.allInstances ?: emptyList()
    val allVariables = instanceHolder?.allVariables ?: emptyList()

    fun addInstanceObject(feature: Feature, instanceObject: InstanceObject) {
        val list = featureMap.computeIfAbsent(feature) {
            mutableListOf()
        }
        list += instanceObject
    }

    fun transformVariables(): List<Variable> {
        for (variable in allVariables) {
            val newVariable = variableTransformer.transform(variable)
            variableMap[variable] = newVariable
        }

        return variableMap.values.toList()
    }

    val name = instanceHolder?.name ?: "Nothing"

    val fullyQualifiedName: String by lazy {
        val parentName = parent?.fullyQualifiedName ?: ""

        "${parentName}__${name}"
    }

    val type: Type
        get() = when (instanceHolder) {
            is Target -> instanceHolder
            is Instance -> instanceHolder.type
            else -> error("Should not happen")
        }
}

val InstanceHolder.allInstances: List<Instance>
    get() = when (this) {
        is Instance -> allInstances
        is Target -> instances
        else -> error("Unknown instance holder type")
    }

val InstanceHolder.allVariables: List<Variable>
    get() = when (this) {
        is Instance -> allVariables
        is Target -> variables
        else -> error("Unknown instance holder type")
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
    private val instanceQueue = LinkedList<InstanceObject>()
    private val processedInstances = mutableListOf<InstanceObject>()

    fun instantiateInstances(target: Target): InstanceObject {
        val rootInstanceObject = InstanceObject(target, null)

        val targetVariables = rootInstanceObject.transformVariables()
//        target.variables += targetVariables
        // TODO target variables mapped, but need to keep until the end of the transformation!

        for ((targetVariable, newVariable) in target.variables.zip(targetVariables)) {
            EcoreUtil2.replace(targetVariable, newVariable)
        }

        rootInstanceObject.instantiateInstances(target.instances)

        while (instanceQueue.any()) {
            val next = instanceQueue.removeFirst()

            next.instantiateInstances(next.allInstances)
        }

        setReferenceBindings()

        transformVariables(target)

        return rootInstanceObject
    }

    private fun transformVariables(target: Target) {
        for (instanceObject in processedInstances) {
            target.variables += instanceObject.transformVariables()
        }
    }

    private fun InstanceObject.instantiateInstances(instances: List<Instance>) {
        for (instance in instances) {
            this.instantiateInstances(instance)
        }
    }

    private fun InstanceObject.instantiateInstances(instance: Instance) {
        val instanceObject = InstanceObject(instance, this)
        instanceQueue += instanceObject
        processedInstances += instanceObject
        place(instance, instanceObject)
    }

    private fun setReferenceBindings() {
        for (instanceObject in processedInstances) {
            instanceObject.setReferenceBindings()
        }
    }

    private fun InstanceObject.setReferenceBindings() {
        require(instanceHolder is Instance)

        for (binding in instanceHolder.bindings) {
            val holder = expressionEvaluator.evaluateInstanceObject(binding.feature.asChainReferenceExpression().dropLast(1))
            val feature = expressionEvaluator.evaluateTypedReference<Feature>(binding.feature.asChainReferenceExpression())
            val held = expressionEvaluator.evaluateInstanceObjectBottomUp(binding.instance)

            holder.place(feature, held)
        }
    }

    private fun InstanceObject.place(feature: Feature, instanceObject: InstanceObject) {
        addInstanceObject(feature, instanceObject)
        if (feature.subsets != null) {
            place(feature.subsets, instanceObject)
        }
    }

}
