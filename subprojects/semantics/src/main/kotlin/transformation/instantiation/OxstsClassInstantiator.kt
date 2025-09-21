/*
 * SPDX-FileCopyrightText: 2023-2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.semantics.transformation.instantiation

import com.google.inject.Inject
import com.google.inject.Singleton
import hu.bme.mit.semantifyr.oxsts.lang.semantics.MultiplicityRangeEvaluator
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.RangeEvaluation
import hu.bme.mit.semantifyr.oxsts.lang.semantics.typesystem.domain.DomainMemberCalculator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.FeatureDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.FeatureKind
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Instance
import java.util.*

@Singleton
class OxstsClassInstantiator {

    @Inject
    private lateinit var domainMemberCalculator: DomainMemberCalculator

    @Inject
    private lateinit var multiplicityRangeEvaluator: MultiplicityRangeEvaluator

    @Inject
    private lateinit var instanceManager: InstanceManager

    fun instantiateModel(inlinedOxsts: InlinedOxsts) {
        instantiateTree(inlinedOxsts)
        resolveCrossReferences(inlinedOxsts)
    }

    private fun instantiateTree(inlinedOxsts: InlinedOxsts) {
        val instanceQueue = LinkedList<Instance>()
        instanceQueue += inlinedOxsts.rootInstance

        while (instanceQueue.any()) {
            val instance = instanceQueue.removeFirst()
            instance.instantiateChildren()
            instanceQueue += instance.children
        }
    }

    private fun Instance.instantiateChildren() {
        val memberCollection = domainMemberCalculator.getMemberCollection(domain)

        val allContainments = memberCollection.declarationHolders.map {
            it.declaration
        }.filterIsInstance<FeatureDeclaration>().filter {
            it.kind == FeatureKind.CONTAINMENT
        }.filter {
            val multiplicityRange = multiplicityRangeEvaluator.evaluate(it)
            multiplicityRange == RangeEvaluation.ONE // TODO: relax instantiation rules
        }.distinct()

        for (containment in allContainments) {
            instanceManager.createInstance(this, containment)
        }
    }

    private fun resolveCrossReferences(inlinedOxsts: InlinedOxsts) {
//        setReferenceBindings(instanceModel.rootInstance)

//        val patternRunner = PatternRunner(resourceSet)
//
//        setDerivedFeatures(instanceModel.rootInstance, patternRunner)
    }

//    private fun setDerivedFeatures(rootInstance: Instance, patternRunner: PatternRunner) {
//        for (instance in rootInstance.treeSequence()) {
//            instance.resolveDerivedFeatures(patternRunner)
//        }
//    }
//
//    private fun setReferenceBindings(rootInstance: Instance) {
//        for (instance in rootInstance.treeSequence()) {
//            instance.resolveReferenceBindings()
//        }
//    }
//
//    private fun Instance.resolveDerivedFeatures(patternRunner: PatternRunner) {
//        for (feature in type.allFeatures.filterIsInstance<Derived>()) {
//            val instances = patternRunner.evaluateOnInstance(this, feature.pattern)
//            instancePlacer.place(feature, instances)
//        }
//    }
//
//    private fun Instance.resolveReferenceBindings() {
//        for (feature in type.allFeatures.filterIsInstance<Reference>()) {
//            if (!feature.isDataType && feature.expression != null) {
//                instancePlacer.place(feature, resolveBinding(feature))
//            }
//        }
//    }
//
//    private fun Instance.resolveBinding(feature: Feature): Set<Instance> = when (feature) {
//        is Containment -> instancePlacer[feature]
//        is Reference -> {
//            if (feature.expression == null) { // not bound reference -> take actual contents
//                // FIXME: what if this reference is subsetted by a bound reference?
//                //  In that case, we must resolve all of its subsetters before
//                //  returning the actual instances!
//                instancePlacer[feature]
//            } else {
//                val chainingExpression = feature.expression as ChainReferenceExpression
//
//                val context = contextualEvaluator.findFirstValidContext(chainingExpression)
//                val holder = context.contextualEvaluator.evaluateInstance(chainingExpression.dropLast(1))
//                val referencedFeature = chainingExpression.typedReferencedElement<Feature>()
//
//                holder.resolveBinding(referencedFeature) // recurse, until a free feature is found
//            }
//        }
//
//        else -> error("Unsupported Feature type: $feature")
//    }

}
