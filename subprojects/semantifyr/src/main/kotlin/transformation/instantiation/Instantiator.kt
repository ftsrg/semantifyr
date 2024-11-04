/*
 * SPDX-FileCopyrightText: 2023-2024 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.semantifyr.transformation.instantiation

import hu.bme.mit.semantifyr.oxsts.model.oxsts.ChainReferenceExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Containment
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Derived
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Feature
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Instance
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Package
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Reference
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Target
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Variable
import hu.bme.mit.semantifyr.oxsts.semantifyr.transformation.pattern.PatternRunner
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.OxstsFactory
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils._package
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.allContainments
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.allFeatures
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.allVariables
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.contextualEvaluator
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.dropLast
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.instancePlacer
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.isDataType
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.treeSequence
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.type
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.typedReferencedElement
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.variableTransformer
import org.eclipse.xtext.EcoreUtil2
import java.util.*

object Instantiator {

    fun instantiateTree(target: Target): Instance {
        val containingPackage = EcoreUtil2.getContainerOfType(target, Package::class.java)

        val rootContainment = OxstsFactory.createContainment().also {
            it.typing = OxstsFactory.createReferenceTyping(target)
            it.name = target.name
            it.multiplicity = OxstsFactory.createOneMultiplicity()
        }
        val rootInstance = OxstsFactory.createInstance(rootContainment)

        containingPackage.features += rootContainment
        containingPackage.instances += rootInstance

        val instanceQueue = LinkedList<Instance>()
        instanceQueue += rootInstance

        while (instanceQueue.any()) {
            val instance = instanceQueue.removeFirst()
            instance.instantiateChildren()
            instanceQueue += instance.children
        }

        setReferenceBindings(rootInstance)

        val resourceSet = EcoreUtil2.getResourceSet(target)
        val patternRunner = PatternRunner(resourceSet)

        setDerivedFeatures(rootInstance, patternRunner)

        return rootInstance
    }

    private fun Instance.instantiateChildren() {
        val allContainments = containment.allContainments

        for (containment in allContainments) {
            fixImplicitType(containment)
            val instance = OxstsFactory.createInstance(containment, this)
            children += instance
            instancePlacer.place(containment, instance)
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

        containment._package.types += newType

        containment.typing = OxstsFactory.createReferenceTyping(newType)
    }

    private fun setDerivedFeatures(rootInstance: Instance, patternRunner: PatternRunner) {
        for (instance in rootInstance.treeSequence()) {
            instance.resolveDerivedFeatures(patternRunner)
        }
    }

    private fun setReferenceBindings(rootInstance: Instance) {
        for (instance in rootInstance.treeSequence()) {
            instance.resolveReferenceBindings()
        }
    }

    fun instantiateVariablesTree(rootInstance: Instance): List<Variable> {
        val variables = mutableListOf<Variable>()

        for (instance in rootInstance.treeSequence()) {
            variables += instance.instantiateVariables()
        }

        return variables
    }

    private fun Instance.instantiateVariables(): List<Variable> {
        val allVariables = containment.allVariables

        for (variable in allVariables) {
            variableTransformer.transform(variable)
        }

        return variableTransformer.allTransformedVariables
    }

    private fun Instance.resolveDerivedFeatures(patternRunner: PatternRunner) {
        for (feature in type.allFeatures.filterIsInstance<Derived>()) {
            val instances = patternRunner.execute(this, feature.pattern)
            instancePlacer.place(feature, instances)
        }
    }

    private fun Instance.resolveReferenceBindings() {
        for (feature in type.allFeatures.filterIsInstance<Reference>()) {
            if (!feature.isDataType && feature.expression != null) {
                instancePlacer.place(feature, resolveBinding(feature))
            }
        }
    }

    private fun Instance.resolveBinding(feature: Feature): Set<Instance> = when (feature) {
        is Containment -> instancePlacer[feature]
        is Reference -> {
            if (feature.expression == null) { // not bound reference -> take actual contents
                // FIXME: what if this reference is subsetted by a bound reference?
                //  In that case, we must resolve all of its subsetters before
                //  returning the actual instances!
                instancePlacer[feature]
            } else {
                val chainingExpression = feature.expression as ChainReferenceExpression

                val context = contextualEvaluator.findFirstValidContext(chainingExpression)
                val holder = context.contextualEvaluator.evaluateInstance(chainingExpression.dropLast(1))
                val referencedFeature = chainingExpression.typedReferencedElement<Feature>()

                holder.resolveBinding(referencedFeature) // recurse, until a free feature is found
            }
        }
        else -> error("Unsupported Feature type: $feature")
    }

}
