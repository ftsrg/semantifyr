package hu.bme.mit.gamma.oxsts.engine.transformation

import hu.bme.mit.gamma.oxsts.engine.utils.allSubsets
import hu.bme.mit.gamma.oxsts.engine.utils.type
import hu.bme.mit.gamma.oxsts.model.oxsts.Feature
import hu.bme.mit.gamma.oxsts.model.oxsts.Containment
import hu.bme.mit.gamma.oxsts.model.oxsts.Type
import hu.bme.mit.gamma.oxsts.model.oxsts.Variable

open class Instance(
    val containment: Containment,
    val parent: Instance?,
) {
    val type = containment.type

    // FIXME: extract all accesses!
    val children = mutableSetOf<Instance>()

    // FIXME: extract all accesses!
    val featureMap = mutableMapOf<Feature, MutableList<Instance>>()
    // FIXME: extract all accesses!
    val variableMap = mutableMapOf<Variable, Variable>()
    // FIXME: extract all accesses!
    val featureEnumMap = mutableMapOf<Feature, EnumMapping>()

    val expressionEvaluator = ExpressionEvaluator(this)
    val operationTransformer = InlineOperationTransformer(this)
    val transitionResolver = TransitionResolver(this)
    val instanceEvaluator = InstanceEvaluator(this)
    val variableTransformer = VariableTransformer(this)

    private val allContainments = containment.allContainments
    private val allVariables = containment.allVariables

    fun instantiateChildren() {
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
        for (variable in allVariables) {
            variableMap[variable] = variableTransformer.featureToEnum(variable)
        }

        return variableMap.values.toList()
    }

    fun place(feature: Feature, instance: Instance) {
        val instances = featureMap.computeIfAbsent(feature) {
            mutableListOf()
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

// TODO create caching containment and variable query

val Containment.allContainments: List<Containment>
    get() = type.allContainments

val Containment.allVariables: List<Variable>
    get() = type.allVariables

val Type.allContainments: List<Containment>
    get() {
        val list = mutableListOf<Containment>()
        list += features.filterIsInstance<Containment>()
        if (supertype != null) {
            list += supertype.allContainments
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

val nothingType = OxstsFactory.createType().also {
    it.name = "NothingType" // TODO: need character readable by Theta, but not writeable by in Oxsts
}
val nothingContainment = OxstsFactory.createContainment().also {
    it.name = "Nothing" // TODO: need character readable by Theta, but not writeable by in Oxsts
    it.typing = OxstsFactory.createReferenceTyping(nothingType)
}

object NothingInstance : Instance(nothingContainment, null)
