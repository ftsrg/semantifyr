/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.semantics.verification

import com.google.inject.Inject
import hu.bme.mit.semantifyr.semantics.reader.SemantifyrLoader
import hu.bme.mit.semantifyr.semantics.transformation.injection.scope.SemantifyrScopes.runInScope
import hu.bme.mit.semantifyr.semantics.utils.unload
import org.eclipse.emf.ecore.EObject
import org.eclipse.emf.ecore.resource.Resource
import org.eclipse.emf.ecore.resource.ResourceSet
import org.eclipse.emf.ecore.util.EcoreUtil

fun interface EObjectRunnable<T : EObject> {
    fun run(eObject: T)
}

fun interface ResourceRunnable {
    fun run(resource: Resource)
}

fun interface ResourceSetRunnable {
    fun run(resourceSet: ResourceSet)
}

class CompilationScopeHelper {

    @Inject
    private lateinit var semantifyrLoader: SemantifyrLoader

    private fun useResourceSetInCompilationScope(resourceSet: ResourceSet, block: Runnable) {
        try {
            runInScope(block)
        } finally {
            resourceSet.unload()
        }
    }

    fun runInCompilationScope(resourceSet: ResourceSet, runnable: ResourceSetRunnable) {
        val compilationResourceSet = semantifyrLoader.cloneOxstsPackagesInResourceSet(resourceSet)

        useResourceSetInCompilationScope(compilationResourceSet) {
            runnable.run(compilationResourceSet)
        }
    }

    fun runInCompilationScope(resource: Resource, runnable: ResourceRunnable) {
        val compilationResourceSet = semantifyrLoader.cloneOxstsPackagesInResourceSet(resource.resourceSet)
        val compilationResource = compilationResourceSet.getResource(resource.uri, true)

        useResourceSetInCompilationScope(compilationResourceSet) {
            runnable.run(compilationResource)
        }
    }

    fun <T : EObject> runInCompilationScope(eObject: T, runnable: EObjectRunnable<T>) {
        val compilationResourceSet = semantifyrLoader.cloneOxstsPackagesInResourceSet(eObject.eResource().resourceSet)

        @Suppress("UNCHECKED_CAST")
        val compilationEObject = compilationResourceSet.getEObject(EcoreUtil.getURI(eObject), true) as T

        useResourceSetInCompilationScope(compilationResourceSet) {
            runnable.run(compilationEObject)
        }
    }

}
