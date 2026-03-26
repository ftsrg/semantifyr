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
import hu.bme.mit.semantifyr.oxsts.model.oxsts.OxstsModelPackage
import hu.bme.mit.semantifyr.semantics.utils.ResourceSetLoader
import hu.bme.mit.semantifyr.semantics.utils.SemantifyrUtils.modelPathsUnder
import hu.bme.mit.semantifyr.semantics.utils.info
import hu.bme.mit.semantifyr.semantics.utils.loggerFactory
import org.eclipse.emf.ecore.resource.Resource
import org.eclipse.emf.ecore.resource.ResourceSet
import org.eclipse.xtext.resource.XtextResourceSet
import java.nio.file.Path

class SemantifyrModelContext(
    val resourceSet: ResourceSet,
    val libraryResources: List<Resource>,
    val modelResources: List<Resource>,
)

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

    fun loadStandaloneModel(path: Path): SemantifyrModelContext {
        return startContext().loadModel(path).buildAndResolve()
    }

    fun startContext(): SemantifyrLoaderContext {
        return SemantifyrLoaderContext()
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

        resourceSetLoader.resolveAllAndValidate(clone)

        return clone
    }

    private fun openResource(resourceSet: ResourceSet, resource: Resource): Resource {
        return resourceSet.getResource(resource.uri, true)
    }

    inner class SemantifyrLoaderContext {
        val resourceSet = createResourceSet()
        val libraryResources = mutableListOf<Resource>()
        val modelResources = mutableListOf<Resource>()

        fun buildAndResolve(): SemantifyrModelContext {
            resourceSetLoader.resolveAllAndValidate(resourceSet)

            return SemantifyrModelContext(resourceSet, libraryResources, modelResources)
        }

        fun loadModel(path: Path): SemantifyrLoaderContext {
            val resource = loadFile(resourceSet, path)
            modelResources += resource

            return this
        }

        fun loadModels(path: Path): SemantifyrLoaderContext {
            for (modelPath in modelPathsUnder(path)) {
                loadModel(modelPath)
            }

            return this
        }

        fun loadLibrary(path: Path): SemantifyrLoaderContext {
            val resource = loadFile(resourceSet, path)
            libraryResources += resource

            return this
        }

        fun loadLibraries(path: Path): SemantifyrLoaderContext {
            for (libraryPath in modelPathsUnder(path)) {
                loadLibrary(libraryPath)
            }

            return this
        }

        private fun loadFile(resourceSet: ResourceSet, path: Path): Resource {
            logger.info { "Reading file: $path" }
            return resourceSet.getResource(resourceUriProvider.createFileUri(path), true)
        }

    }

}
