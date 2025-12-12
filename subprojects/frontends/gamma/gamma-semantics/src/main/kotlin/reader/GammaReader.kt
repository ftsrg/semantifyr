/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.gamma.semantics.reader

import com.google.inject.Inject
import com.google.inject.Provider
import com.google.inject.Singleton
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.GammaModelPackage
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
import java.io.File
import java.nio.file.Path
import kotlin.io.path.absolutePathString

@Singleton
class GammaReader {

    @Inject
    private lateinit var resourceSetProvider: Provider<XtextResourceSet>

    @Inject
    private lateinit var resourceFactory: IResourceFactory

    @Inject
    private lateinit var resourceValidator: IResourceValidator

    fun readGammaFile(file: File): GammaModelPackage {
        val resource = loadStandaloneModel(file.toPath())

        return resource.contents.single() as GammaModelPackage
    }

    private fun loadStandaloneModel(model: Path): Resource {
        val resourceSet = resourceSetProvider.get()
        val resource = loadFile(resourceSet, model)
        resolveAndValidate(resourceSet)
        return resource
    }

    private fun loadFile(resourceSet: ResourceSet, path: Path): Resource {
        val resource = resourceFactory.createResource(URI.createFileURI(path.absolutePathString()))
        resource.load(mapOf<Any, Any>())
        resourceSet.resources += resource
        return resource
    }

    private fun resolveAndValidate(resourceSet: ResourceSet) {
        EcoreUtil2.resolveAll(resourceSet)
        for (resource in resourceSet.resources) {
            validateResource(resource)
        }
    }

    private fun validateResource(resource: Resource) {
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
