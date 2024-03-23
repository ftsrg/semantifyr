package hu.bme.mit.gamma.oxsts.engine.transformation

import hu.bme.mit.gamma.oxsts.engine.utils.allFeatures
import hu.bme.mit.gamma.oxsts.engine.utils.allSubsets
import hu.bme.mit.gamma.oxsts.model.oxsts.Feature

class FeatureContainer(
    private val holder: Instance
) {

    private val featureValueContainers = holder.type.allFeatures.associateWith {
        FeatureValueContainer(it)
    }

    fun place(feature: Feature, instance: Instance) {
        val container = featureValueContainers[feature] ?: error("Feature can not be found on instance $holder")

        container.instances += instance

        for (subsetFeature in feature.allSubsets) {
            place(subsetFeature, instance)
        }
    }

    fun place(feature: Feature, instances: Collection<Instance>) {
        for (instance in instances) {
            place(feature, instance)
        }
    }

    operator fun get(feature: Feature): Set<Instance> {
        return featureValueContainers[feature]?.instances ?: error("")
    }

}

class FeatureValueContainer(
    val feature: Feature
) {

    val instances = mutableSetOf<Instance>()

}
