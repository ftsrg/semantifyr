/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.semantics.transformation.instantiation

import hu.bme.mit.semantifyr.oxsts.model.oxsts.Instance
import hu.bme.mit.semantifyr.semantics.transformation.injection.scope.CompilationScoped

object NameHelper {
    const val ROOT_INSTANCE_NAME = ""
    const val INSTANCE_NAME_SEPARATOR = "$$"
}

@CompilationScoped
class InstanceNameProvider {

    private val nameCache = mutableMapOf<Instance, String>()

    fun getInstanceName(instance: Instance): String {
        return nameCache.getOrPut(instance) {
            computeInstanceName(instance)
        }
    }

    private fun computeInstanceName(instance: Instance): String {
        if (instance.parent == null) {
            return NameHelper.ROOT_INSTANCE_NAME
        }

        val parentName = getInstanceName(instance.parent)

        return "$parentName${NameHelper.INSTANCE_NAME_SEPARATOR}${instance.domain.name}"
    }

}
