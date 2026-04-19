/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.instantiation

import hu.bme.mit.semantifyr.oxsts.model.oxsts.DomainDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.FeatureDeclaration

class Instance(
    val domain: DomainDeclaration,
    val parent: Instance?,
    val tree: InstanceTree,
) {

    private val slots = mutableMapOf<FeatureDeclaration, MutableSet<Instance>>()

    val children: Collection<Instance>
        get() = slots.values.flatten()

    val name: String by lazy {
        if (parent == null) {
            InstanceNames.ROOT_INSTANCE_NAME
        } else {
            "${parent.name}${InstanceNames.INSTANCE_NAME_SEPARATOR}${domain.name}"
        }
    }

    fun instancesAt(feature: FeatureDeclaration): Set<Instance> {
        return slots[feature] ?: emptySet()
    }

    internal fun placeInSlot(feature: FeatureDeclaration, instance: Instance) {
        slots.getOrPut(feature) { mutableSetOf() }.add(instance)
    }

}
