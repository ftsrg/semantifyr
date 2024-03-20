package hu.bme.mit.gamma.oxsts.engine.transformation

import hu.bme.mit.gamma.oxsts.engine.utils.allContainments
import hu.bme.mit.gamma.oxsts.engine.utils.allFeatures
import hu.bme.mit.gamma.oxsts.engine.utils.allSubsets
import hu.bme.mit.gamma.oxsts.engine.utils.allVariables
import hu.bme.mit.gamma.oxsts.engine.utils.type
import hu.bme.mit.gamma.oxsts.model.oxsts.Feature
import hu.bme.mit.gamma.oxsts.model.oxsts.Containment
import hu.bme.mit.gamma.oxsts.model.oxsts.Variable

class FeatureValueManager(
    val instance: Instance
) {

    val featureValueContainers = instance.type.allFeatures.associateWith {
        FeatureValueContainer(instance, it)
    }

}

class FeatureValueContainer(
    val instance: Instance,
    val feature: Feature
) {

    lateinit var value: DataType

}

open class Instance(
    val containment: Containment,
    val parent: Instance?,
) {
    val type = containment.type

    // FIXME: extract all accesses!
    val children = mutableSetOf<Instance>()

    val featureValueManager = FeatureValueManager(this)

    // FIXME: extract all accesses!
    val featureMap = mutableMapOf<Feature, MutableSet<Instance>>()

    // FIXME: extract all accesses!
    val variableMap = mutableMapOf<Variable, Variable>()
    // FIXME: extract all accesses!
    val featureEnumMap = mutableMapOf<Feature, EnumMapping>()

    val expressionEvaluator = ExpressionEvaluator(this)
    val operationTransformer = OperationInliner(this)
    val transitionResolver = TransitionResolver(this)
    val featureEvaluator = FeatureEvaluator(this)
    val variableTransformer = VariableTransformer(this)

    fun instantiateChildren() {
        val allContainments = containment.allContainments

        for (containment in allContainments) {
            fixImplicitType(containment)
            val instance = Instance(containment, this)
            children += instance
            place(containment, instance)
        }
    }

    fun fixImplicitType(containment: Containment) {
        // TODO: workaround, since we do not support multi-inheritance for now
        if (containment.features.none()) {
            return
        }

        val newType = OxstsFactory.createType().also {
            it.name = "${containment.name}__implicit"
            it.features += containment.features
            it.supertype = containment.type
        }
        containment.typing = OxstsFactory.createReferenceTyping(newType)
    }

    fun instantiateVariables(): List<Variable> {
        val allVariables = containment.allVariables

        for (variable in allVariables) {
            variableMap[variable] = variableTransformer.featureToEnum(variable)
        }

        return variableMap.values.toList()
    }

    fun place(feature: Feature, instance: Instance) {
        val instances = featureMap.computeIfAbsent(feature) {
            mutableSetOf()
        }
        instances += instance
        for (subset in feature.allSubsets) {
            place(subset, instance)
        }
    }

    fun place(feature: Feature, instances: Collection<Instance>) {
        for (instance in instances) {
            place(feature, instance)
        }
    }

    val name = containment.name

    val fullyQualifiedName: String by lazy {
        val parentName = parent?.fullyQualifiedName ?: ""

        "${parentName}__${name}"
    }

}

val nothingType = OxstsFactory.createType().also {
    it.name = "NothingType" // TODO: need character readable by Theta, but not writeable by in Oxsts
}
val nothingContainment = OxstsFactory.createContainment().also {
    it.name = "Nothing" // TODO: need character readable by Theta, but not writeable by in Oxsts
    it.typing = OxstsFactory.createReferenceTyping(nothingType)
}

object NothingInstance : Instance(nothingContainment, null)
