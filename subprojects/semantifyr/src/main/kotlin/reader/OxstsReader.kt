/*
 * SPDX-FileCopyrightText: 2023-2024 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.semantifyr.reader

import hu.bme.mit.semantifyr.oxsts.lang.OxstsStandaloneSetup
import hu.bme.mit.semantifyr.oxsts.model.oxsts.OxstsPackage
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Package
import hu.bme.mit.semantifyr.oxsts.semantifyr.commands.CompileCommand
import org.eclipse.emf.common.util.URI
import org.eclipse.emf.ecore.resource.Resource
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl
import org.eclipse.viatra.query.patternlanguage.emf.EMFPatternLanguageStandaloneSetup
import org.eclipse.viatra.query.patternlanguage.emf.vql.PatternLanguagePackage
import org.eclipse.xtext.EcoreUtil2
import org.eclipse.xtext.resource.XtextResource
import org.eclipse.xtext.util.CancelIndicator
import org.eclipse.xtext.validation.CheckMode
import org.slf4j.LoggerFactory
import java.io.File

fun File.walkFiles() = walkTopDown().filter { it.isFile }

fun prepareOxsts() {
    OxstsStandaloneSetup.doSetup()
    OxstsPackage.eINSTANCE.name

    EMFPatternLanguageStandaloneSetup.doSetup()
    PatternLanguagePackage.eINSTANCE.name
}

class OxstsReader(
    private val libraryDirectory: String
) {

    val logger = LoggerFactory.getLogger(CompileCommand::class.java)!!

    val resourceSet = ResourceSetImpl()
    val userResources = mutableListOf<Resource>()

    val rootElements
        get() = userResources.flatMap { it.contents }.map { it as Package }

    init {
        resourceSet.loadOptions[XtextResource.OPTION_ENCODING] = "UTF-8"
    }

    fun readFile(file: File): Resource {
        logger.info("Reading file: $file")
        val resource = resourceSet.getResource(URI.createFileURI(file.path), true)
        resource.load(emptyMap<Any, Any>())

        return resource
    }

    fun readDirectory(modelDirectory: String) {
        logger.info("Reading library files from $libraryDirectory")

        val libraryFiles = File(libraryDirectory).walkFiles().filter {
            it.extension == "oxsts"
        }

        for (file in libraryFiles) {
            readFile(file)
        }

        logger.info("Reading model files from $modelDirectory")

        val modelFiles = File(modelDirectory).walkFiles().filter {
            it.extension == "oxsts"
        }

        for (file in modelFiles) {
            userResources += readFile(file)
        }

        validateResources()
    }

    fun readModel(modelPath: String) {
        logger.info("Reading library files from $libraryDirectory")

        val modelFile = File(modelPath)
        val modelAbsolutePath = modelFile.absolutePath

        val libraryFiles = File(libraryDirectory).walkFiles().filter {
            it.extension == "oxsts"
        }.filterNot {
            it.absolutePath == modelAbsolutePath
        }

        for (file in libraryFiles) {
            readFile(file)
        }

        logger.info("Reading model file")

        userResources += readFile(modelFile)

        validateResources()
    }

    private fun validateResources() {
        logger.info("Validating resources")

        for (resource in resourceSet.resources) {
            EcoreUtil2.resolveAll(resource)
            if (resource.errors.any()) {
                logger.error("Errors found in file (${resource.uri.toFileString()})}")

                for (error in resource.errors) {
                    logger.error(error.message)
                }

                error("Errors found in file (${resource.uri.toFileString()})}")
            }
            if (resource.warnings.any()) {
                logger.warn("Warnings found in file (${resource.uri.toFileString()})")

                for (warning in resource.warnings) {
                    logger.warn(warning.message)
                }
            }
            val validator = (resource as XtextResource).resourceServiceProvider.resourceValidator
            val issues = validator.validate(resource, CheckMode.ALL, CancelIndicator.NullImpl)
            if (issues.any()) {
                logger.error("Issues found in file (${resource.uri.toFileString()})}")

                for (issue in issues) {
                    logger.error(issue.message)
                }

                error("Issues found in file (${resource.uri.toFileString()})")
            }
        }

        logger.info("Validation successful!")
    }

}
