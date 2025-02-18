/*
 * SPDX-FileCopyrightText: 2023-2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.semantifyr.reader

import hu.bme.mit.semantifyr.oxsts.lang.OxstsStandaloneSetup
import hu.bme.mit.semantifyr.oxsts.model.oxsts.OxstsPackage
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Package
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.XtextResourceValidator.validateAndLoadResourceSet
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.info
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.loggerFactory
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.walkFiles
import org.eclipse.emf.common.util.URI
import org.eclipse.emf.ecore.resource.Resource
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl
import org.eclipse.viatra.query.patternlanguage.emf.EMFPatternLanguageStandaloneSetup
import org.eclipse.viatra.query.patternlanguage.emf.vql.PatternLanguagePackage
import org.eclipse.xtext.resource.XtextResource
import java.io.File

fun prepareOxsts() {
    OxstsStandaloneSetup.doSetup()
    OxstsPackage.eINSTANCE.name

    EMFPatternLanguageStandaloneSetup.doSetup()
    PatternLanguagePackage.eINSTANCE.name
}

class OxstsReader(
    private val libraryDirectory: String
) {

    val logger by loggerFactory()

    val resourceSet = ResourceSetImpl()
    val userResources = mutableListOf<Resource>()

    val rootElements
        get() = userResources.flatMap { it.contents }.map { it as Package }

    init {
        resourceSet.loadOptions[XtextResource.OPTION_ENCODING] = "UTF-8"
    }

    fun readFile(file: File): Resource {
        logger.info { "Reading file: $file" }
        return resourceSet.getResource(URI.createFileURI(file.path), true)
    }

    fun readDirectory(modelDirectory: String) {
        logger.info { "Reading library files from $libraryDirectory" }

        val libraryFiles = File(libraryDirectory).walkFiles().filter {
            it.extension == "oxsts"
        }

        for (file in libraryFiles) {
            readFile(file)
        }

        logger.info { "Reading model files from $modelDirectory" }

        val modelFiles = File(modelDirectory).walkFiles().filter {
            it.extension == "oxsts"
        }

        for (file in modelFiles) {
            userResources += readFile(file)
        }

        validateAndLoadResourceSet(resourceSet)
    }

    fun readModel(modelPath: String) {
        logger.info { "Reading library files from $libraryDirectory" }

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

        validateAndLoadResourceSet(resourceSet)
    }

}
