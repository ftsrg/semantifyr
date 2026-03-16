/*
 * SPDX-FileCopyrightText: 2023-2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.semantics.reader

import com.google.inject.Inject
import com.google.inject.Provider
import hu.bme.mit.semantifyr.oxsts.lang.library.LibraryAdapterFinder
import hu.bme.mit.semantifyr.oxsts.lang.utils.ResourceUriProvider
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ClassDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.OxstsModelPackage
import hu.bme.mit.semantifyr.semantics.utils.ResourceSetLoader
import hu.bme.mit.semantifyr.semantics.utils.info
import hu.bme.mit.semantifyr.semantics.utils.loggerFactory
import org.eclipse.emf.ecore.resource.Resource
import org.eclipse.emf.ecore.resource.ResourceSet
import org.eclipse.xtext.resource.XtextResourceSet
import java.nio.file.Path

class SemantifyrModelContext(
    val resourceSet: ResourceSet,
    val modelResource: List<Resource>
) {
    fun streamClasses(): Sequence<ClassDeclaration> {
        return modelResource.asSequence().mapNotNull {
            it.contents.first() as? OxstsModelPackage
        }.flatMap {
            it.declarations
        }.filterIsInstance(ClassDeclaration::class.java)
    }
}

class SemantifyrLoader {

    private val logger by loggerFactory()

    @Inject
    private lateinit var resourceSetProvider: Provider<XtextResourceSet>

    @Inject
    private lateinit var resourceSetLoader: ResourceSetLoader

    @Inject
    private lateinit var libraryAdapterFinder: LibraryAdapterFinder

    @Inject
    private lateinit var resourceUriProvider: ResourceUriProvider

    private fun createResourceSet(): XtextResourceSet {
        val resourceSet = resourceSetProvider.get()
        libraryAdapterFinder.getOrInstall(resourceSet)
        return resourceSet
    }

    private fun loadFile(resourceSet: ResourceSet, path: Path): Resource {
        logger.info { "Reading file: $path" }
        val resource = resourceSet.getResource(resourceUriProvider.createFileUri(path), true)
        return resource
    }

    fun loadStandaloneModelContext(model: Path): SemantifyrModelContext {
        val resourceSet = createResourceSet()
        val resource = loadFile(resourceSet, model)
        resourceSetLoader.resolveAndValidate(resourceSet)
        return SemantifyrModelContext(
            resourceSet,
            listOf(resource)
        )
    }

    fun loadStandaloneModel(model: Path): Resource {
        val resourceSet = createResourceSet()
        val resource = loadFile(resourceSet, model)
        resourceSetLoader.resolveAndValidate(resourceSet)
        return resource
    }

    fun cloneOxstsPackagesInResourceSet(resourceSet: ResourceSet): ResourceSet {
        val clone = createResourceSet()

        // For some reason resourceSet.resources may change in the loop leading to concurrent modification
        val resourcesCopy = resourceSet.resources.toList()
        for (resource in resourcesCopy) {
            if (resource.contents.singleOrNull() is OxstsModelPackage) {
                openResource(clone, resource)
            }
        }

        resourceSetLoader.resolveAndValidate(clone)

        return clone
    }

    private fun openResource(resourceSet: ResourceSet, resource: Resource): Resource {
        return resourceSet.getResource(resource.uri, true)
    }

}
