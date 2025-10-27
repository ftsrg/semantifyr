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
import org.eclipse.xtext.resource.XtextResourceSet
import org.eclipse.xtext.util.CancelIndicator
import org.eclipse.xtext.validation.CheckMode
import org.eclipse.xtext.validation.IResourceValidator
import java.nio.file.Path
import kotlin.io.path.absolutePathString

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
    private lateinit var resourceValidator: IResourceValidator

    @Inject
    private lateinit var libraryAdapterFinder: LibraryAdapterFinder

    private fun createResourceSet(): XtextResourceSet {
        val resourceSet = resourceSetProvider.get()
        libraryAdapterFinder.getOrInstall(resourceSet)
        return resourceSet
    }

    private fun loadFile(resourceSet: ResourceSet, path: Path): Resource {
        logger.info { "Reading file: $path" }
        val resource = resourceSet.getResource(URI.createFileURI(path.absolutePathString()), true)
        return resource
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

}
