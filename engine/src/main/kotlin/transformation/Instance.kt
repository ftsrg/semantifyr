package hu.bme.mit.gamma.oxsts.engine.transformation

import hu.bme.mit.gamma.oxsts.engine.utils.allContainments
import hu.bme.mit.gamma.oxsts.engine.utils.allVariables
import hu.bme.mit.gamma.oxsts.engine.utils.type
import hu.bme.mit.gamma.oxsts.model.oxsts.Containment
import hu.bme.mit.gamma.oxsts.model.oxsts.Feature
import hu.bme.mit.gamma.oxsts.model.oxsts.Variable

open class Instance(
    val containment: Containment,
    val parent: Instance?,
) {
    val type = containment.type


    // TODO: should be immutable outside of this class
    val children = mutableSetOf<Instance>()

    val featureContainer = FeatureContainer(this)

    val expressionEvaluator = ExpressionEvaluator(this)
    val featureEvaluator = FeatureEvaluator(this)

    val transitionResolver = TransitionResolver(this)

    val operationInliner = OperationInliner(this)
    val variableTransformer = VariableTransformer(this)

    val name = containment.name

    val fullyQualifiedName: String by lazy {
        val parentName = parent?.fullyQualifiedName ?: ""

        "${parentName}__${name}"
    }

    fun instantiateChildren() {
        val allContainments = containment.allContainments

        for (containment in allContainments) {
            fixImplicitType(containment)
            val instance = Instance(containment, this)
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

    fun instantiateVariables(): List<Variable> {
        val allVariables = containment.allVariables

        for (variable in allVariables) {
            variableTransformer.transform(variable)
        }

        return variableTransformer.allTransformedVariables
    }

}

val nothingType = OxstsFactory.createType().also {
    it.name = "NothingType" // TODO: need character that is valid in XSTS, but not in OXSTS
}
val nothingContainment = OxstsFactory.createContainment().also {
    it.name = "Nothing" // TODO: need character that is valid in XSTS, but not in OXSTS
    it.typing = OxstsFactory.createReferenceTyping(nothingType)
}

object NothingInstance : Instance(nothingContainment, null)
