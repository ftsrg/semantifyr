/*
 * SPDX-FileCopyrightText: 2023-2024 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.engine.reader

import hu.bme.mit.semantifyr.oxsts.lang.OxstsStandaloneSetup
import hu.bme.mit.semantifyr.oxsts.model.oxsts.OxstsPackage
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Package
import org.eclipse.emf.common.util.URI
import org.eclipse.emf.ecore.resource.Resource
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl
import org.eclipse.viatra.query.patternlanguage.emf.EMFPatternLanguageStandaloneSetup
import org.eclipse.viatra.query.patternlanguage.emf.vql.PatternLanguagePackage
import org.eclipse.xtext.EcoreUtil2
import org.eclipse.xtext.resource.XtextResource
import org.eclipse.xtext.util.CancelIndicator
import org.eclipse.xtext.validation.CheckMode
import java.io.File

fun File.walkFiles() = walkTopDown().filter { it.isFile }

fun prepareOxsts() {
    OxstsStandaloneSetup.doSetup()
    OxstsPackage.eINSTANCE.name

    EMFPatternLanguageStandaloneSetup.doSetup()
    PatternLanguagePackage.eINSTANCE.name
}

class OxstsReader(
    private val inputDirectory: String,
    private val libraryDirectory: String = ""
) {
    val resourceSet = ResourceSetImpl()
    val userResources = mutableListOf<Resource>()

    val rootElements
        get() = userResources.flatMap { it.contents }.map { it as Package }

    init {
        resourceSet.loadOptions[XtextResource.OPTION_ENCODING] = "UTF-8"
    }

    fun read() {
        if (libraryDirectory.isNotBlank()) {
            val libraryFile = File(libraryDirectory)

            for (file in libraryFile.walkFiles().filter { it.extension == "oxsts" }) {
                val resource = resourceSet.getResource(URI.createURI(file.path), true)
                resource.load(emptyMap<Any, Any>())
            }
        }

        val inputFile = File(inputDirectory)

        for (file in inputFile.walkFiles().filter { it.extension == "oxsts" }) {
            val resource = resourceSet.getResource(URI.createURI(file.path), true)
            resource.load(emptyMap<Any, Any>())
            userResources += resource
        }

        for (resource in resourceSet.resources) {
            EcoreUtil2.resolveAll(resource)
            if (resource.errors.any()) {
                println(resource.errors)
                error("Errors found in file (${resource.uri.toFileString()}):\n${resource.errors.joinToString("\n")}")
            }
            if (resource.warnings.any()) {
                println("Warnings found in file (${resource.uri.toFileString()}):\n${resource.warnings.joinToString("\n")}")
            }
            val validator = (resource as XtextResource).resourceServiceProvider.resourceValidator
            val issues = validator.validate(resource, CheckMode.ALL, CancelIndicator.NullImpl)
            for (issue in issues) {
                println("Issues found in file (${resource.uri.toFileString()}):")
                println("${issue.severity} - ${issue.message}")
            }
        }

    }

}
