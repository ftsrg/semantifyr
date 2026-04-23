/*
 * SPDX-FileCopyrightText: 2023-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.reader

import com.google.inject.Inject
import com.google.inject.Provider
import hu.bme.mit.semantifyr.oxsts.lang.library.LibraryAdapterFinder
import hu.bme.mit.semantifyr.oxsts.lang.utils.ResourceUriProvider
import hu.bme.mit.semantifyr.oxsts.model.oxsts.OxstsModelPackage
import hu.bme.mit.semantifyr.logging.info
import hu.bme.mit.semantifyr.logging.loggerFactory
import hu.bme.mit.semantifyr.logging.warn
import hu.bme.mit.semantifyr.oxsts.lang.library.OxstsLibrary

import org.eclipse.emf.ecore.resource.Resource
import org.eclipse.emf.ecore.resource.ResourceSet
import org.eclipse.xtext.resource.XtextResourceSet
import java.nio.file.Files as NioFiles
import java.nio.file.Path
import kotlin.io.path.PathWalkOption
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.walk

class SemantifyrModelContext(
    val resourceSet: ResourceSet,
    val libraryResources: List<Resource>,
    val modelResources: List<Resource>,
)

class SemantifyrLoader @Inject constructor(
    private val resourceSetProvider: Provider<XtextResourceSet>,
    private val resourceSetLoader: ResourceSetLoader,
    private val libraryAdapterFinder: LibraryAdapterFinder,
    private val resourceUriProvider: ResourceUriProvider,
) {

    private val logger by loggerFactory()

    fun startContext(): SemantifyrLoaderContext {
        return SemantifyrLoaderContext()
    }

    private fun createResourceSet(): XtextResourceSet {
        val resourceSet = resourceSetProvider.get()
        libraryAdapterFinder.getOrInstall(resourceSet)
        return resourceSet
    }

    fun loadStandaloneModel(path: Path): SemantifyrModelContext {
        return startContext().loadModel(path).buildAndResolve()
    }

    fun fromResourceSet(resourceSet: ResourceSet): SemantifyrModelContext {
        val clonedResourceSet = cloneOxstsPackagesInResourceSet(resourceSet)
        return SemantifyrModelContext(
            resourceSet = clonedResourceSet,
            libraryResources = emptyList(),
            modelResources = clonedResourceSet.resources.toList(),
        )
    }

    private fun cloneOxstsPackagesInResourceSet(resourceSet: ResourceSet): ResourceSet {
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
            if (! isOxstsFile(path)) {
                logger.warn { "Tried to load non-oxsts file, skipping: $path" }
            }

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
            if (! isOxstsFile(path)) {
                logger.warn { "Tried to load non-oxsts file, skipping: $path" }
            }

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

        fun loadLibraryPaths(paths: List<Path>): SemantifyrLoaderContext {
            for (path in paths) {
                if (NioFiles.isDirectory(path)) {
                    loadLibraries(path)
                } else {
                    loadLibrary(path)
                }
            }
            return this
        }

        fun loadModelPaths(paths: List<Path>): SemantifyrLoaderContext {
            for (path in paths) {
                if (NioFiles.isDirectory(path)) {
                    loadModels(path)
                } else {
                    loadModel(path)
                }
            }
            return this
        }

        private fun loadFile(resourceSet: ResourceSet, path: Path): Resource {
            logger.info { "Reading file: $path" }
            return resourceSet.getResource(resourceUriProvider.createFileUri(path), true)
        }

    }

    private fun isOxstsFile(path: Path): Boolean {
        return path.name.endsWith(OxstsLibrary.FILE_NAME_SUFFIX)
    }

    private fun modelPathsUnder(path: Path): Sequence<Path> {
        return path.walk(PathWalkOption.FOLLOW_LINKS).filter {
            it.isRegularFile() && isOxstsFile(it)
        }
    }

}
