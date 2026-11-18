/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.testing

import org.eclipse.emf.ecore.resource.Resource
import org.eclipse.xtext.resource.IResourceServiceProvider
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionContext

class GlobalRegistriesExtension :
    BeforeAllCallback,
    AfterAllCallback {

    private val namespace = ExtensionContext.Namespace.create(GlobalRegistriesExtension::class.java)

    override fun beforeAll(context: ExtensionContext) {
        val snapshots = globalMaps().map {
            HashMap(it)
        }
        context.getStore(namespace).put(SNAPSHOTS, snapshots)
    }

    override fun afterAll(context: ExtensionContext) {
        @Suppress("UNCHECKED_CAST")
        val snapshots = context.getStore(namespace).get(SNAPSHOTS) as List<Map<String, Any>>
        for ((map, snapshot) in globalMaps().zip(snapshots)) {
            map.clear()
            map.putAll(snapshot)
        }
    }

    private fun globalMaps(): List<MutableMap<String, Any>> {
        val serviceProviders = IResourceServiceProvider.Registry.INSTANCE
        val resourceFactories = Resource.Factory.Registry.INSTANCE
        return listOf(
            serviceProviders.extensionToFactoryMap,
            serviceProviders.contentTypeToFactoryMap,
            serviceProviders.protocolToFactoryMap,
            resourceFactories.extensionToFactoryMap,
            resourceFactories.contentTypeToFactoryMap,
            resourceFactories.protocolToFactoryMap,
        )
    }

    private companion object {
        const val SNAPSHOTS = "snapshots"
    }
}
