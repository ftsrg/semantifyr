/*
 * SPDX-FileCopyrightText: 2023-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.reader

import com.google.inject.Inject
import com.google.inject.Provider
import hu.bme.mit.semantifyr.logging.info
import hu.bme.mit.semantifyr.logging.loggerFactory
import hu.bme.mit.semantifyr.logging.warn
import hu.bme.mit.semantifyr.oxsts.lang.library.LibraryAdapterFinder
import hu.bme.mit.semantifyr.oxsts.lang.library.OxstsLibrary
import hu.bme.mit.semantifyr.oxsts.lang.utils.ResourceUriProvider
import hu.bme.mit.semantifyr.oxsts.lang.utils.SelfContainedResource
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.OxstsModelPackage
import org.eclipse.emf.common.util.URI
import org.eclipse.emf.ecore.resource.Resource
import org.eclipse.emf.ecore.resource.ResourceSet
import org.eclipse.xtext.EcoreUtil2
import org.eclipse.xtext.resource.XtextResource
import org.eclipse.xtext.resource.XtextResourceSet
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Path
import kotlin.io.path.PathWalkOption
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.walk
import java.nio.file.Files as NioFiles

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

    // every OXSTS resource is copied from the open buffer or its own content, since compiling rewrites the clone in place
    fun fromResourceSet(resourceSet: ResourceSet, openDocumentTexts: Map<URI, String>): SemantifyrModelContext {
        val clone = createResourceSet()

        // For some reason resourceSet.resources may change in the loop leading to concurrent modification
        for (resource in resourceSet.resources.toList()) {
            if (!isOxstsModel(resource) || clone.getResource(resource.uri, false) != null) {
                continue
            }
            val text = openDocumentTexts[resource.uri] ?: contentOf(resource)
            val cloned = openResourceFromText(clone, resource.uri, text)
            if (cloned.contents.singleOrNull() is InlinedOxsts) {
                SelfContainedResource.mark(cloned)
            }
        }

        EcoreUtil2.resolveAll(clone)
        for (resource in clone.resources.toList()) {
            if (resource.contents.singleOrNull() !is InlinedOxsts) {
                resourceSetLoader.validateResource(resource)
            }
        }

        return SemantifyrModelContext(
            resourceSet = clone,
            libraryResources = emptyList(),
            modelResources = clone.resources.toList(),
        )
    }

    private fun isOxstsModel(resource: Resource): Boolean {
        val root = resource.contents.singleOrNull()
        return root is OxstsModelPackage || root is InlinedOxsts
    }

    private fun contentOf(resource: Resource): String {
        val rootNode = (resource as? XtextResource)?.parseResult?.rootNode
        if (rootNode != null) {
            return rootNode.text
        }
        val output = ByteArrayOutputStream()
        resource.save(output, mapOf(XtextResource.OPTION_ENCODING to Charsets.UTF_8.name()))
        return output.toString(Charsets.UTF_8)
    }

    private fun openResourceFromText(
        resourceSet: ResourceSet,
        uri: URI,
        text: String,
    ): Resource {
        val resource = resourceSet.createResource(uri)
        resource.load(ByteArrayInputStream(text.toByteArray(Charsets.UTF_8)), mapOf(XtextResource.OPTION_ENCODING to Charsets.UTF_8.name()))
        return resource
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
                logger.warn { "Skipped $path, which is not an OXSTS file" }
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
                logger.warn { "Skipped $path, which is not an OXSTS file" }
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
