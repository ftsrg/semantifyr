/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.semantics.transformation.instantiation

import com.google.inject.Inject
import com.google.inject.Singleton
import hu.bme.mit.semantifyr.oxsts.lang.utils.OnResourceSetChangeEvictingCache
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Instance
import org.eclipse.xtext.util.Tuples

object NameHelper {
    const val ROOT_INSTANCE_NAME = ""
    const val INSTANCE_NAME_SEPARATOR = "$$"
}

@Singleton
class InstanceNameProvider {

    private val CACHE_KEY: String = "${javaClass.canonicalName}.CACHE_KEY"

    @Inject
    private lateinit var resourceScopeCache: OnResourceSetChangeEvictingCache

    fun getInstanceName(instance: Instance): String {
        return resourceScopeCache.get(Tuples.create(CACHE_KEY, instance), instance.eResource()) {
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
