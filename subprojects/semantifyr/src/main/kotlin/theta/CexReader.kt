/*
 * SPDX-FileCopyrightText: 2023-2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.semantifyr.theta

import hu.bme.mit.semantifyr.cex.lang.CexStandaloneSetup
import hu.bme.mit.semantifyr.cex.lang.cex.Cex
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.XtextResourceValidator
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.loggerFactory
import org.eclipse.emf.common.util.URI
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl
import org.eclipse.xtext.resource.XtextResource
import java.io.File

fun prepareCex() {
    CexStandaloneSetup.doSetup()
}

class CexReader {
    val logger by loggerFactory()

    val resourceSet = ResourceSetImpl()

    init {
        resourceSet.loadOptions[XtextResource.OPTION_ENCODING] = "UTF-8"
    }

    fun readCexFile(file: File): Cex {
        val resource = resourceSet.getResource(URI.createFileURI(file.path), true)

        XtextResourceValidator.validateAndLoadResource(resource)

        return resource.contents.single() as Cex
    }
}
