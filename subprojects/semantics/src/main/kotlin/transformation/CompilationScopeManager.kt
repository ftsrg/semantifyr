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
import org.eclipse.xtext.EcoreUtil2
import org.eclipse.xtext.diagnostics.Severity
import org.eclipse.xtext.resource.XtextResourceSet
import org.eclipse.xtext.util.CancelIndicator
import org.eclipse.xtext.validation.CheckMode
import org.eclipse.xtext.validation.IResourceValidator

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

    @Inject
    private lateinit var resourceValidator: IResourceValidator

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

        resolveAndValidate(resourceSet)

        return compilationResourceSet
    }

    private fun openResource(resourceSet: ResourceSet, resource: Resource): Resource {
        return resourceSet.getResource(resource.uri, true)
    }

    private fun resolveAndValidate(resourceSet: ResourceSet) {
        EcoreUtil2.resolveAll(resourceSet)
        for (resource in resourceSet.resources) {
            validateResource(resource)
        }
    }

    fun validateResource(resource: Resource) {
        val issues = resourceValidator.validate(resource, CheckMode.ALL, CancelIndicator.NullImpl)

        if (resource.errors.any()) {
            error("Errors found in file (${resource.uri.toFileString()})}")
        }
        if (issues.any()) {
            if (issues.any { it.severity == Severity.ERROR || it.severity == Severity.WARNING }) {
                error("Issues found in file!")
            }
        }
    }

}
