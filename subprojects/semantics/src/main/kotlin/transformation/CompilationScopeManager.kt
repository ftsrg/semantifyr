/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.semantics.transformation

import com.google.inject.Inject
import com.google.inject.Provider
import com.google.inject.Singleton
import com.google.inject.name.Named
import hu.bme.mit.semantifyr.oxsts.lang.library.LibraryAdapterFinder
import hu.bme.mit.semantifyr.oxsts.model.oxsts.OxstsModelPackage
import hu.bme.mit.semantifyr.semantics.transformation.injection.scope.CompilationScope
import hu.bme.mit.semantifyr.semantics.transformation.injection.scope.CompilationScopeContext
import org.eclipse.emf.ecore.EObject
import org.eclipse.emf.ecore.resource.Resource
import org.eclipse.emf.ecore.resource.ResourceSet
import org.eclipse.emf.ecore.util.EcoreUtil
import org.eclipse.xtext.resource.XtextResourceSet

@FunctionalInterface
interface ResourceSetRunnable {

    fun run(resourceSet: ResourceSet)

}

@FunctionalInterface
interface ResourceRunnable {

    fun run(resource: Resource)

}

@FunctionalInterface
interface EObjectRunnable<T : EObject> {

    fun run(eObject: T)

}

@Singleton
class CompilationScopeManager {

    @Inject
    @Named("compilationScope")
    private lateinit var compilationScope: CompilationScope

    @Inject
    private lateinit var resourceSetProvider: Provider<XtextResourceSet>

    @Inject
    private lateinit var libraryAdapterFinder: LibraryAdapterFinder

    private fun createResourceSet(): XtextResourceSet {
        val resourceSet = resourceSetProvider.get()
        libraryAdapterFinder.getOrInstall(resourceSet)
        return resourceSet
    }

    private fun unloadResourceSet(resourceSet: ResourceSet) {
        resourceSet.resources.forEach {
            it.unload()
        }
        resourceSet.resources.clear()
    }

    private inline fun runCompilationScope(resourceSet: ResourceSet, block: () -> Unit) {
        val compilationScopeContext = CompilationScopeContext()
        compilationScope.enter(compilationScopeContext)
        try {
            block()
        } finally {
            compilationScope.exit()
            unloadResourceSet(resourceSet)
        }
    }

    fun runInCompilationScope(resourceSet: ResourceSet, runnable: ResourceSetRunnable) {
        val compilationResourceSet = copyResourceSet(resourceSet)

        runCompilationScope(compilationResourceSet) {
            runnable.run(compilationResourceSet)
        }
    }

    fun runInCompilationScope(resource: Resource, runnable: ResourceRunnable) {
        val compilationResourceSet = copyResourceSet(resource.resourceSet)
        val compilationResource = openResource(compilationResourceSet, resource)

        runCompilationScope(compilationResourceSet) {
            runnable.run(compilationResource)
        }
    }

    fun <T : EObject> runInCompilationScope(eObject: T, runnable: EObjectRunnable<T>) {
        val compilationResourceSet = copyResourceSet(eObject.eResource().resourceSet)
        val compilationEObject = compilationResourceSet.getEObject(EcoreUtil.getURI(eObject), true) as T

        runCompilationScope(compilationResourceSet) {
            runnable.run(compilationEObject)
        }
    }

    private fun copyResourceSet(resourceSet: ResourceSet): ResourceSet {
        val compilationResourceSet = createResourceSet()

        for (resource in resourceSet.resources) {
            if (resource.contents.singleOrNull() is OxstsModelPackage) {
                openResource(compilationResourceSet, resource)
            }
        }

        return compilationResourceSet
    }

    private fun openResource(resourceSet: ResourceSet, resource: Resource): Resource {
        return resourceSet.getResource(resource.uri, true)
    }

}
