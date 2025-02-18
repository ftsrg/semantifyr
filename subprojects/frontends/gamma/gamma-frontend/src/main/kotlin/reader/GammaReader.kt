/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.gamma.frontend.reader

import hu.bme.mit.semantifyr.frontends.gamma.lang.GammaStandaloneSetup
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.Package
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.XtextResourceValidator
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.loggerFactory
import org.eclipse.emf.common.util.URI
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl
import org.eclipse.xtext.resource.XtextResource
import java.io.File

fun prepareGamma() {
    GammaStandaloneSetup.doSetup()
}

class GammaReader {
    val logger by loggerFactory()

    val resourceSet = ResourceSetImpl()

    init {
        resourceSet.loadOptions[XtextResource.OPTION_ENCODING] = "UTF-8"
    }

    fun readGammaFile(file: File): Package {
        val resource = resourceSet.getResource(URI.createFileURI(file.path), true)

        XtextResourceValidator.validateAndLoadResource(resource)

        return resource.contents.single() as Package
    }
}
