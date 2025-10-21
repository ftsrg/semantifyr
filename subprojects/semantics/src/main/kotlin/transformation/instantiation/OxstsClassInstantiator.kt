/*
 * SPDX-FileCopyrightText: 2023-2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.semantics.transformation.instantiation

import com.google.inject.Inject
import hu.bme.mit.semantifyr.oxsts.lang.scoping.domain.DomainMemberCollectionProvider
import hu.bme.mit.semantifyr.oxsts.lang.semantics.MultiplicityRangeEvaluator
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.RangeEvaluation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.FeatureDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.FeatureKind
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Instance
import hu.bme.mit.semantifyr.semantics.transformation.injection.scope.CompilationScoped
import java.util.*

@CompilationScoped
class OxstsClassInstantiator {

    @Inject
    private lateinit var domainMemberCollectionProvider: DomainMemberCollectionProvider

    @Inject
    private lateinit var multiplicityRangeEvaluator: MultiplicityRangeEvaluator

    @Inject
    private lateinit var instanceManager: InstanceManager

    fun instantiateModel(inlinedOxsts: InlinedOxsts) {
        instantiateTree(inlinedOxsts)
    }

    private fun instantiateTree(inlinedOxsts: InlinedOxsts) {
        val instanceQueue = LinkedList<Instance>()
        instanceQueue += inlinedOxsts.rootInstance

        while (instanceQueue.any()) {
            val instance = instanceQueue.removeFirst()
            instantiateChildren(instance)
            instanceQueue += instance.children
        }
    }

    private fun instantiateChildren(instance: Instance) {
        val memberCollection = domainMemberCollectionProvider.getMemberCollection(instance.domain)

        val allContainments = memberCollection.declarations.filterIsInstance<FeatureDeclaration>().filter {
            it.kind == FeatureKind.CONTAINMENT
        }.filter {
            val multiplicityRange = multiplicityRangeEvaluator.evaluate(it)
            multiplicityRange == RangeEvaluation.ONE // TODO: relax instantiation rules
        }.distinct()

        for (containment in allContainments) {
            instanceManager.createAndPlaceInstance(instance, containment)
        }
    }

}
