/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.semantics.transformation.instantiation

import com.google.inject.Inject
import hu.bme.mit.semantifyr.oxsts.lang.library.builtin.BuiltinAnnotationHandler
import hu.bme.mit.semantifyr.oxsts.lang.semantics.OppositeHandler
import hu.bme.mit.semantifyr.oxsts.lang.semantics.typesystem.domain.DomainMemberCalculator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Association
import hu.bme.mit.semantifyr.oxsts.model.oxsts.DomainDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.FeatureDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Instance
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableMapping
import hu.bme.mit.semantifyr.semantics.expression.RedefinitionAwareReferenceResolver
import hu.bme.mit.semantifyr.semantics.transformation.injection.scope.CompilationScoped
import hu.bme.mit.semantifyr.semantics.utils.OxstsFactory

class VariableManager(
    instance: Instance,
    variables: List<VariableDeclaration>
) {

    private val variableMappings = mutableMapOf<VariableDeclaration, VariableMapping>()

    init {
        for (variable in variables) {
            val mapping = OxstsFactory.createVariableMapping(variable)
            instance.variableMappings += mapping
            variableMappings.put(variable, mapping)
        }
    }

    fun resolve(variableDeclaration: VariableDeclaration): VariableDeclaration {
        return variableMappings[variableDeclaration]!!.actual
    }

    fun actualVariables(): Collection<VariableDeclaration> {
        return variableMappings.values.map {
            it.actual
        }
    }

}

class AssociationManager(
    instance: Instance,
    features: List<FeatureDeclaration>
) {

    private val associations = mutableMapOf<FeatureDeclaration, Association>()

    init {
        for (feature in features) {
            val association = OxstsFactory.createAssociation(feature)
            instance.associations += association
            associations.put(feature, association)
        }
    }

    fun instancesAt(featureDeclaration: FeatureDeclaration): Set<Instance> {
        return associations[featureDeclaration]!!.instances.toSet()
    }

    fun place(featureDeclaration: FeatureDeclaration, instance: Instance) {
        associations[featureDeclaration]!!.instances.add(instance)
    }

}

@CompilationScoped
class InstanceManager {

    @Inject
    private lateinit var domainMemberCalculator: DomainMemberCalculator

    @Inject
    private lateinit var redefinitionAwareReferenceResolver: RedefinitionAwareReferenceResolver

    @Inject
    private lateinit var oppositeHandler: OppositeHandler

    @Inject
    private lateinit var builtinAnnotationHandler: BuiltinAnnotationHandler

    private val variableManagers = mutableMapOf<Instance, VariableManager>()
    private val associationManagers = mutableMapOf<Instance, AssociationManager>()
    private val sharedInstances = mutableMapOf<FeatureDeclaration, Instance>()

    fun createAndPlaceInstance(holder: Instance, featureDeclaration: FeatureDeclaration) {
        if (builtinAnnotationHandler.isSharedFeature(featureDeclaration)) {
            createAndPlaceSharedInstance(holder, featureDeclaration)
        } else {
            createAndPlaceIndividualInstance(holder, featureDeclaration)
        }
    }

    fun createAndPlaceIndividualInstance(holder: Instance, featureDeclaration: FeatureDeclaration) {
        val instance = createInstance(featureDeclaration)
        holder.children += instance
        placeInstance(holder, featureDeclaration, instance)
    }

    fun createAndPlaceSharedInstance(holder: Instance, featureDeclaration: FeatureDeclaration) {
        val instance = sharedInstances.getOrPut(featureDeclaration) {
            createInstance(featureDeclaration).also {
                holder.children += it
            }
        }
        placeInstance(holder, featureDeclaration, instance)
    }

    fun createInstance(domainDeclaration: DomainDeclaration): Instance {
        val instance = OxstsFactory.createInstance(domainDeclaration)

        createVariableMappings(instance)
        createFeatureMappings(instance)

        return instance
    }

    private fun createVariableMappings(instance: Instance) {
        val memberCollection = domainMemberCalculator.getMemberCollection(instance.domain)

        val allVariables = memberCollection.declarationHolders.map {
            it.declaration
        }.filterIsInstance<VariableDeclaration>().distinct()

        variableManagers.put(instance, VariableManager(instance, allVariables))
    }

    private fun createFeatureMappings(instance: Instance) {
        val memberCollection = domainMemberCalculator.getMemberCollection(instance.domain)

        val allFeatures = memberCollection.declarationHolders.map {
            it.declaration
        }.filterIsInstance<FeatureDeclaration>().distinct()

        associationManagers.put(instance, AssociationManager(instance, allFeatures))
    }

    fun placeInstance(holder: Instance, featureDeclaration: FeatureDeclaration, held: Instance) {
        localPlaceInstance(holder, featureDeclaration, held)

        val opposite = oppositeHandler.getOppositeFeature(featureDeclaration)
        if (opposite != null) {
            localPlaceInstance(held, opposite, holder)
        }
//        superSetHandler.getSuperSetFeatures(featureDeclaration).asSequence().forEach {
//            placeInstance(holder, it, held)
//        }
    }

    private fun localPlaceInstance(holder: Instance, featureDeclaration: FeatureDeclaration, held: Instance) {
        val feature = redefinitionAwareReferenceResolver.resolve(holder, featureDeclaration) as FeatureDeclaration

        associationManagers[holder]!!.place(feature, held)
    }

    fun resolveVariable(holder: Instance, variableDeclaration: VariableDeclaration): VariableDeclaration {
        return variableManagers[holder]!!.resolve(variableDeclaration)
    }

    fun instancesAt(holder: Instance, featureDeclaration: FeatureDeclaration): Set<Instance> {
        return associationManagers[holder]!!.instancesAt(featureDeclaration)
    }

    fun actualVariables(holder: Instance): Collection<VariableDeclaration> {
        return variableManagers[holder]!!.actualVariables()
    }

}
