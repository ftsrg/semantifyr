package hu.bme.mit.gamma.oxsts.engine.transformation

import hu.bme.mit.gamma.oxsts.engine.utils.allContainments
import hu.bme.mit.gamma.oxsts.engine.utils.allVariables
import hu.bme.mit.gamma.oxsts.engine.utils.type
import hu.bme.mit.gamma.oxsts.model.oxsts.Containment
import hu.bme.mit.gamma.oxsts.model.oxsts.Instance
import hu.bme.mit.gamma.oxsts.model.oxsts.Variable
import hu.bme.mit.gamma.oxsts.model.oxsts.impl.InstanceImpl

val Instance.type
    get() = containment.type

val Instance.name
    get() = containment.name

private class InstanceExtensions(
    val instance: Instance
) {

    val featureContainer = FeatureContainer(instance)
    val expressionEvaluator = ExpressionEvaluator(instance)
    val featureEvaluator = FeatureEvaluator(instance)
    val transitionResolver = TransitionResolver(instance)
    val operationInliner = OperationInliner(instance)
    val variableTransformer = VariableTransformer(instance)

    val fullyQualifiedName: String by lazy {
        val parentName = instance.parent?.fullyQualifiedName ?: ""

        "${parentName}__${instance.name}"
    }
}

private val instanceExtensionsMap = mutableMapOf<Instance, InstanceExtensions>()
private val Instance.extensions
    get() = instanceExtensionsMap.computeIfAbsent(this) {
        InstanceExtensions(this)
    }

val Instance.featureContainer
    get() = extensions.featureContainer
val Instance.expressionEvaluator
    get() = extensions.expressionEvaluator
val Instance.featureEvaluator
    get() = extensions.featureEvaluator
val Instance.transitionResolver
    get() = extensions.transitionResolver
val Instance.operationInliner
    get() = extensions.operationInliner
val Instance.variableTransformer
    get() = extensions.variableTransformer
val Instance.fullyQualifiedName
    get() = extensions.fullyQualifiedName

fun Instance.instantiateChildren() {
    val allContainments = containment.allContainments

    for (containment in allContainments) {
        fixImplicitType(containment)
        val instance = OxstsFactory.createInstance(containment, this)
        children += instance
        featureContainer.place(containment, instance)
    }
}

private fun fixImplicitType(containment: Containment) {
    // TODO: workaround, since we do not support multi-inheritance for now

    if (
        containment.features.none() &&
        containment.havocTransition.none() &&
        containment.initTransition.none() &&
        containment.mainTransition.none() &&
        containment.transitions.none()
    ) {
        return
    }

    val newType = OxstsFactory.createType().also {
        it.name = "${containment.name}__implicit"
        it.features += containment.features
        it.havocTransition += containment.havocTransition
        it.initTransition += containment.initTransition
        it.mainTransition += containment.mainTransition
        it.transitions += containment.transitions
        it.supertype = containment.type
    }
    containment.typing = OxstsFactory.createReferenceTyping(newType)
}

fun Instance.instantiateVariables(): List<Variable> {
    val allVariables = containment.allVariables

    for (variable in allVariables) {
        variableTransformer.transform(variable)
    }

    return variableTransformer.allTransformedVariables
}

private val nothingType = OxstsFactory.createType().also {
    it.name = "NothingType" // TODO: need character that is valid in XSTS, but not in OXSTS
}
private val nothingContainment = OxstsFactory.createContainment().also {
    it.name = "Nothing" // TODO: need character that is valid in XSTS, but not in OXSTS
    it.typing = OxstsFactory.createReferenceTyping(nothingType)
}

object NothingInstance : InstanceImpl() {

    init {
        containment = nothingContainment
    }

}
