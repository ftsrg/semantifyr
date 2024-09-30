/*
 * SPDX-FileCopyrightText: 2023-2024 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.compiler.utils

import hu.bme.mit.semantifyr.oxsts.compiler.transformation.OperationInliner
import hu.bme.mit.semantifyr.oxsts.compiler.transformation.evaluation.ContextualExpressionEvaluator
import hu.bme.mit.semantifyr.oxsts.compiler.transformation.evaluation.FeatureEvaluator
import hu.bme.mit.semantifyr.oxsts.compiler.transformation.instantiation.InstancePlacer
import hu.bme.mit.semantifyr.oxsts.compiler.transformation.instantiation.VariableTransformer
import hu.bme.mit.semantifyr.oxsts.compiler.transformation.resolution.TransitionResolver
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Instance
import hu.bme.mit.semantifyr.oxsts.model.oxsts.impl.InstanceImpl

val Instance.type
    get() = containment.type

val Instance.name
    get() = containment.name

private class InstanceExtensions(
    val instance: Instance
) {

    val instancePlacer = InstancePlacer(instance)
    val contextualEvaluator = ContextualExpressionEvaluator(instance)
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

val Instance.instancePlacer
    get() = extensions.instancePlacer
val Instance.contextualEvaluator
    get() = extensions.contextualEvaluator
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

private val nothingType = OxstsFactory.createType().also {
    it.name = "NothingType" // TODO: need character that is valid in XSTS, but not in OXSTS
}
private val nothingContainment = OxstsFactory.createContainment().also {
    it.name = "Nothing" // TODO: need character that is valid in XSTS, but not in OXSTS
    it.typing = OxstsFactory.createReferenceTyping(nothingType)
}

// TODO: should this be added to the package during transformation?
object NothingInstance : InstanceImpl() {

    init {
        containment = nothingContainment
    }

}
