/*
 * SPDX-FileCopyrightText: 2023-2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.semantics.loading

import com.google.inject.Inject
import com.google.inject.Provider
import hu.bme.mit.semantifyr.oxsts.lang.library.LibraryAdapterFinder
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ClassDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.OxstsModelPackage
import hu.bme.mit.semantifyr.semantics.utils.error
import hu.bme.mit.semantifyr.semantics.utils.info
import hu.bme.mit.semantifyr.semantics.utils.loggerFactory
import hu.bme.mit.semantifyr.semantics.utils.warn
import org.eclipse.emf.common.util.URI
import org.eclipse.emf.ecore.resource.Resource
import org.eclipse.emf.ecore.resource.ResourceSet
import org.eclipse.xtext.EcoreUtil2
import org.eclipse.xtext.diagnostics.Severity
import org.eclipse.xtext.resource.IResourceFactory
import org.eclipse.xtext.resource.XtextResourceSet
import org.eclipse.xtext.util.CancelIndicator
import org.eclipse.xtext.validation.CheckMode
import org.eclipse.xtext.validation.IResourceValidator
import java.nio.file.Path
import kotlin.io.path.absolutePathString

class SemantifyrModelContext(
    val resourceSet: ResourceSet,
    val resources: List<Resource>
) {
    fun streamClasses(): Sequence<ClassDeclaration> {
        return resources.asSequence().mapNotNull {
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
    private lateinit var resourceFactory: IResourceFactory

    @Inject
    private lateinit var resourceValidator: IResourceValidator

    @Inject
    private lateinit var libraryAdapterFinder: LibraryAdapterFinder

    private val extraPaths = mutableListOf<Path>()

    private fun createResourceSet(): XtextResourceSet {
        val resourceSet = resourceSetProvider.get()
        val libraryAdapter = libraryAdapterFinder.getOrInstall(resourceSet)
        libraryAdapter.setAdditionalPaths(extraPaths)
        return resourceSet
    }

    private fun loadFile(resourceSet: ResourceSet, path: Path): Resource {
        logger.info { "Reading file: $path" }
        val resource = resourceFactory.createResource(URI.createFileURI(path.absolutePathString()))
        resource.load(mapOf<Any, Any>())
        resourceSet.resources += resource
        return resource
    }

    fun extraPaths(libraryPaths: List<Path>) {
        extraPaths.addAll(libraryPaths)
    }

    fun extraPath(libraryPath: Path) {
        extraPaths.add(libraryPath)
    }

    fun loadStandaloneModelContext(model: Path): SemantifyrModelContext {
        val resourceSet = createResourceSet()
        val resource = loadFile(resourceSet, model)
        resolveAndValidate(resourceSet)
        return SemantifyrModelContext(
            resourceSet,
            listOf(resource)
        )
    }

    fun loadStandaloneModel(model: Path): Resource {
        val resourceSet = createResourceSet()
        val resource = loadFile(resourceSet, model)
        resolveAndValidate(resourceSet)
        return resource
    }

    private fun resolveAndValidate(resourceSet: ResourceSet) {
        libraryAdapterFinder.getOrInstall(resourceSet).loadLibraryResources()
        EcoreUtil2.resolveAll(resourceSet)
        for (resource in resourceSet.resources) {
            validateResource(resource)
        }
    }

    fun validateResource(resource: Resource) {
        val issues = resourceValidator.validate(resource, CheckMode.ALL, CancelIndicator.NullImpl)

        if (resource.errors.any()) {
            logger.error { "Errors found in file (${resource.uri.toFileString()})" }

            for (error in resource.errors) {
                logger.error(error.message)
            }

            error("Errors found in file (${resource.uri.toFileString()})}")
        }
        if (resource.warnings.any()) {
            logger.warn { "Warnings found in file (${resource.uri.toFileString()})" }

            for (warning in resource.warnings) {
                logger.warn(warning.message)
            }
        }
        if (issues.any()) {
            logger.info { "Issues found in file (${resource.uri.toFileString()})" }

            for (issue in issues) {
                when (issue.severity) {
                    Severity.INFO -> {
                        logger.info { "${issue.uriToProblem.toFileString()}[${issue.lineNumber}:${issue.column}] ${issue.message}" }
                    }

                    Severity.WARNING -> {
                        logger.warn { "${issue.uriToProblem.toFileString()}[${issue.lineNumber}:${issue.column}] ${issue.message}" }
                    }

                    Severity.ERROR -> {
                        logger.error { "${issue.uriToProblem.toFileString()}[${issue.lineNumber}:${issue.column}] ${issue.message}" }
                    }

                    else -> {}
                }
            }

            if (issues.any { it.severity == Severity.ERROR || it.severity == Severity.WARNING }) {
                error("Issues found in file!")
            }
        }
    }

//    fun loadDirectoryModel(directory: Path): List<Resource> {
//        val file = directory.toFile()
//        val resourceSet = createResourceSet()
//    }

//    fun readDirectory(modelDirectory: String) {
//        logger.info { "Reading library files from $libraryDirectory" }
//
//        val libraryFiles = File(libraryDirectory).walkFiles().filter {
//            it.extension == "oxsts"
//        }
//
//        for (file in libraryFiles) {
//            readFile(file)
//        }
//
//        logger.info { "Reading model files from $modelDirectory" }
//
//        val modelFiles = File(modelDirectory).walkFiles().filter {
//            it.extension == "oxsts"
//        }
//
//        for (file in modelFiles) {
//            userResources += readFile(file)
//        }
//
//        validateAndLoadResourceSet(resourceSet)
//    }
//
//    fun readModel(modelPath: String) {
//        logger.info { "Reading library files from $libraryDirectory" }
//
//        val modelFile = File(modelPath)
//        val modelAbsolutePath = modelFile.absolutePath
//
//        val libraryFiles = File(libraryDirectory).walkFiles().filter {
//            it.extension == "oxsts"
//        }.filterNot {
//            it.absolutePath == modelAbsolutePath
//        }
//
//        for (file in libraryFiles) {
//            readFile(file)
//        }
//
//        logger.info("Reading model file")
//
//        userResources += readFile(modelFile)
//
//        validateAndLoadResourceSet(resourceSet)
//    }

}
