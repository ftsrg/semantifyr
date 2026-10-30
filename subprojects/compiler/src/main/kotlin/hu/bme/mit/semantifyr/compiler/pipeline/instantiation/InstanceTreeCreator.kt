/*
 * SPDX-FileCopyrightText: 2023-2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.instantiation

import com.google.inject.Inject
import hu.bme.mit.semantifyr.compiler.pipeline.expression.RedefinitionAwareReferenceResolver
import hu.bme.mit.semantifyr.logging.debug
import hu.bme.mit.semantifyr.logging.info
import hu.bme.mit.semantifyr.logging.loggerFactory
import hu.bme.mit.semantifyr.oxsts.lang.library.builtin.BuiltinAnnotationHandler
import hu.bme.mit.semantifyr.oxsts.lang.scoping.domain.DomainMemberCollectionProvider
import hu.bme.mit.semantifyr.oxsts.lang.semantics.OppositeHandler
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.RangeEvaluation
import hu.bme.mit.semantifyr.oxsts.lang.semantics.typesystem.ExpressionTypeEvaluatorProvider
import hu.bme.mit.semantifyr.oxsts.model.oxsts.DomainDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.FeatureDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.FeatureKind

class InstanceTreeCreator @Inject constructor(
    private val domainMemberCollectionProvider: DomainMemberCollectionProvider,
    private val redefinitionAwareReferenceResolver: RedefinitionAwareReferenceResolver,
    private val oppositeHandler: OppositeHandler,
    private val builtinAnnotationHandler: BuiltinAnnotationHandler,
    private val expressionTypeEvaluatorProvider: ExpressionTypeEvaluatorProvider,
) {

    private val logger by loggerFactory()

    fun create(domainDeclaration: DomainDeclaration): InstanceTree {
        logger.info { "Instantiating '${domainDeclaration.name}'" }
        val instanceTree = MutableInstanceTree(domainDeclaration)

        val instanceQueue = ArrayDeque<Instance>()
        instanceQueue += instanceTree.rootInstance

        var visited = 0
        while (instanceQueue.any()) {
            val instance = instanceQueue.removeFirst()
            logger.debug { "Expanding instance '${instance.name}' (queue=${instanceQueue.size})" }
            instantiateChildren(instance, instanceTree)
            instanceQueue += instance.children
            visited++
        }

        logger.info { "Instantiation produced $visited instance(s)" }

        return instanceTree.asInstanceTree()
    }

    private fun instantiateChildren(instance: Instance, instanceTree: MutableInstanceTree) {
        val memberCollection = domainMemberCollectionProvider.getMemberCollection(instance.domain)

        val allContainments = memberCollection.declarations.filterIsInstance<FeatureDeclaration>().filter {
            it.kind == FeatureKind.CONTAINMENT
        }.filter {
            val multiplicityRange = expressionTypeEvaluatorProvider.getEvaluator(it).fromTypeSpecification(it.typeSpecification).range
            multiplicityRange == RangeEvaluation.ONE // TODO: relax instantiation rules
        }.distinct()

        for (containment in allContainments) {
            instanceTree.createAndPlaceInstance(instance, containment)
        }
    }

    inner class MutableInstanceTree(rootDomain: DomainDeclaration) : InstanceTree {

        override val rootInstance: Instance = Instance(
            domain = rootDomain,
            parent = null,
            tree = this,
        )

        private val sharedInstances = mutableMapOf<FeatureDeclaration, Instance>()

        fun createAndPlaceInstance(holder: Instance, featureDeclaration: FeatureDeclaration) {
            if (builtinAnnotationHandler.isSharedFeature(featureDeclaration)) {
                createAndPlaceSharedInstance(holder, featureDeclaration)
            } else {
                createAndPlaceIndividualInstance(holder, featureDeclaration)
            }
        }

        fun createAndPlaceIndividualInstance(holder: Instance, featureDeclaration: FeatureDeclaration) {
            val instance = createInstance(featureDeclaration, holder)
            placeInstance(holder, featureDeclaration, instance)
        }

        fun createAndPlaceSharedInstance(holder: Instance, featureDeclaration: FeatureDeclaration) {
            val instance = sharedInstances.getOrPut(featureDeclaration) {
                createInstance(featureDeclaration, holder)
            }
            placeInstance(holder, featureDeclaration, instance)
        }

        private fun createInstance(domainDeclaration: DomainDeclaration, parent: Instance): Instance {
            return Instance(
                domain = domainDeclaration,
                parent = parent,
                tree = this,
            )
        }

        private fun placeInstance(
            holder: Instance,
            featureDeclaration: FeatureDeclaration,
            held: Instance,
        ) {
            localPlaceInstance(holder, featureDeclaration, held)

            val opposite = oppositeHandler.getOppositeFeature(featureDeclaration)
            if (opposite != null) {
                localPlaceInstance(held, opposite, holder)
            }
        }

        private fun localPlaceInstance(
            holder: Instance,
            featureDeclaration: FeatureDeclaration,
            held: Instance,
        ) {
            val feature = redefinitionAwareReferenceResolver.resolve(holder.domain, featureDeclaration) as FeatureDeclaration
            holder.placeInSlot(feature, held)
        }

        fun asInstanceTree(): InstanceTree {
            return this
        }

    }

}
