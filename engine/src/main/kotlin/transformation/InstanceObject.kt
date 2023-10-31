package hu.bme.mit.gamma.oxsts.engine.transformation

import hu.bme.mit.gamma.oxsts.model.oxsts.Feature
import hu.bme.mit.gamma.oxsts.model.oxsts.Instance
import hu.bme.mit.gamma.oxsts.model.oxsts.InstanceHolder
import hu.bme.mit.gamma.oxsts.model.oxsts.Target
import hu.bme.mit.gamma.oxsts.model.oxsts.Type
import hu.bme.mit.gamma.oxsts.model.oxsts.Variable

open class InstanceObject(
    val instanceHolder: InstanceHolder?,
    val parent: InstanceObject?,
) {
    val children = mutableSetOf<InstanceObject>()

    val featureMap = mutableMapOf<Feature, MutableList<InstanceObject>>()
    val variableMap = mutableMapOf<Variable, Variable>()
    val featureEnumMap = mutableMapOf<Feature, EnumMapping>()

    val expressionEvaluator = ExpressionEvaluator(this)
    val operationEvaluator = InlineOperationEvaluator(this)
    val transitionEvaluator = TransitionEvaluator(this)
    val variableTransformer = VariableTransformer(this)

    private val allInstances = instanceHolder?.allInstances ?: emptyList()
    private val allVariables = instanceHolder?.allVariables ?: emptyList()

    fun instantiateChildren() {
        for (instance in allInstances) {
            val instanceObject = InstanceObject(instance, this)
            children += instanceObject
            place(instance, instanceObject)
        }
    }

    fun instantiateVariables(): List<Variable> {
        for (variable in allVariables) {
            variableMap[variable] = variableTransformer.transform(variable)
        }

        return variableMap.values.toList()
    }

    fun place(feature: Feature, instanceObject: InstanceObject) {
        val instances = featureMap.computeIfAbsent(feature) {
            mutableListOf()
        }
        instances += instanceObject
        if (feature.subsets != null) {
            place(feature.subsets, instanceObject)
        }
    }

    val name = instanceHolder?.name ?: "Nothing"

    val fullyQualifiedName: String by lazy {
        val parentName = parent?.fullyQualifiedName ?: ""

        "${parentName}__${name}"
    }

    val type by lazy {
        when (instanceHolder) {
            is Target -> instanceHolder
            is Instance -> instanceHolder.type
            else -> error("Unknown type of InstanceHolder $instanceHolder")
        }
    }
}

val InstanceHolder.allInstances: List<Instance>
    get() = when (this) {
        is Instance -> allInstances
        is Target -> allInstances
        else -> error("Unknown type of InstanceHolder $this")
    }

val InstanceHolder.allVariables: List<Variable>
    get() = when (this) {
        is Instance -> allVariables
        is Target -> (this as Type).allVariables
        else -> error("Unknown type of InstanceHolder $this")
    }

val Target.allInstances: List<Instance>
    get() {
        val list = mutableListOf<Instance>()
        list += features.filterIsInstance<Instance>()
        list += instances
        if (supertype != null) {
            list += if (supertype is Target) {
                (supertype as Target).allInstances
            } else {
                supertype.allInstances
            }
        }
        return list
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
