/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.gamma.reader

import com.google.inject.Inject
import com.google.inject.Provider
import hu.bme.mit.semantifyr.compiler.reader.ResourceSetLoader
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.GammaModelPackage
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.GammaRoot
import hu.bme.mit.semantifyr.logging.info
import hu.bme.mit.semantifyr.logging.loggerFactory
import org.eclipse.emf.common.util.URI
import org.eclipse.emf.ecore.resource.Resource
import org.eclipse.emf.ecore.resource.ResourceSet
import org.eclipse.xtext.EcoreUtil2
import org.eclipse.xtext.resource.XtextResourceSet
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension

class GammaReader @Inject constructor(
    private val resourceSetLoader: ResourceSetLoader,
    private val resourceSetProvider: Provider<XtextResourceSet>,
) {

    private val logger by loggerFactory()

    fun readGammaFile(
        file: File,
        workspace: Path? = null,
    ): GammaModelPackage {
        return readGammaFiles(listOf(file), workspace).single() as GammaModelPackage
    }

    fun workspaceFiles(workspace: Path): List<File> {
        return Files.walk(workspace).use { paths ->
            paths.filter {
                it.extension in MODEL_EXTENSIONS
            }.map {
                it.toFile()
            }.toList()
        }
    }

    // the Gamma models beside the read files, or under the given workspace, are what imports resolve in; only what the read files reach is validated
    fun readGammaFiles(
        files: List<File>,
        workspace: Path? = null,
    ): List<GammaRoot> {
        logger.info { "Reading Gamma sources $files" }
        val resourceSet = resourceSetProvider.get()
        val resources = files.map {
            loadFile(resourceSet, it.toPath())
        }
        val workspaceFiles = if (workspace != null) {
            workspaceFiles(workspace)
        } else {
            files.mapNotNull {
                it.absoluteFile.parentFile?.listFiles()?.toList()
            }.flatten().filter {
                it.extension in MODEL_EXTENSIONS
            }.distinct()
        }
        for (file in workspaceFiles) {
            loadFile(resourceSet, file.toPath())
        }

        val reached = reachedResources(resources)
        for (resource in reached) {
            EcoreUtil2.resolveAll(resource)
        }
        for (resource in reached) {
            resourceSetLoader.validateResource(resource)
        }

        return resources.map {
            it.contents.single() as GammaRoot
        }
    }

    private fun reachedResources(resources: List<Resource>): Set<Resource> {
        val reached = resources.toMutableSet()
        val pending = ArrayDeque(resources)
        while (pending.isNotEmpty()) {
            val root = pending.removeFirst().contents.singleOrNull() as? GammaRoot ?: continue
            val packageNames = root.imports.mapNotNull {
                it.importedPackage?.takeUnless { importedPackage ->
                    importedPackage.eIsProxy()
                }?.name
            }.toSet() + listOfNotNull((root as? GammaModelPackage)?.name)
            val packageResources = root.eResource().resourceSet.resources.filter {
                (it.contents.singleOrNull() as? GammaModelPackage)?.name in packageNames
            }
            for (resource in packageResources) {
                if (reached.add(resource)) {
                    pending += resource
                }
            }
        }
        return reached
    }

    private fun loadFile(
        resourceSet: ResourceSet,
        path: Path,
    ): Resource {
        val resource = resourceSet.getResource(URI.createFileURI(path.toAbsolutePath().normalize().toString()), true)
        resourceSet.resources += resource
        return resource
    }

    companion object {
        private val MODEL_EXTENSIONS = setOf(
            "gamma",
            "gcd",
        )
    }

}
