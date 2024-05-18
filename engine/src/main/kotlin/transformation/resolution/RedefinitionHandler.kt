package hu.bme.mit.semantifyr.oxsts.engine.transformation.resolution

import hu.bme.mit.semantifyr.oxsts.engine.utils.isRedefine
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Feature
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Type

object RedefinitionHandler {

    private val mappings = mutableMapOf<Type, RedefinitionMapping>()

    private fun handleRedefinitions(type: Type) {
        if (mappings.containsKey(type)) {
            return
        }

        val mapping = if (type.supertype == null) {
            RedefinitionMapping()
        } else {
            handleRedefinitions(type.supertype)
            mappings[type.supertype]!!.clone()
        }

        mappings[type] = mapping

        for (feature in type.features) {
            mapping.features[feature] = feature
            if (feature.isRedefine) {
                mapping.features[feature.redefines] = feature
            }
        }
    }

    fun resolveFeature(type: Type, feature: Feature): Feature {
        handleRedefinitions(type)

        return mappings[type]!!.features[feature] ?: error("Type (${type.name}) does not contain a feature named ${feature.name}")
    }

}

class RedefinitionMapping(
    val features: MutableMap<Feature, Feature> = mutableMapOf()
) {

    fun clone(): RedefinitionMapping {
        return RedefinitionMapping(features.toMutableMap())
    }

}
