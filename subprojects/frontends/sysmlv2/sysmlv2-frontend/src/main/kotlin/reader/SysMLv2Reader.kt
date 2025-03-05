/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.sysmlv2.frontend.reader

import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.XtextResourceValidator.validateAndLoadResourceSet
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.info
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.loggerFactory
import org.omg.kerml.xtext.KerMLStandaloneSetup
import org.omg.sysml.lang.sysml.util.SysMLLibraryUtil
import org.omg.sysml.util.SysMLUtil
import org.omg.sysml.xtext.SysMLStandaloneSetup
import java.io.File

fun prepareSysMLv2() {
    KerMLStandaloneSetup.doSetup()
    SysMLStandaloneSetup.doSetup()
}

class SysMLv2Reader(
    private val modelPath: File,
    private val libraryFile: File,
) : SysMLUtil() {

    init {
        addExtension(".kerml")
        addExtension(".sysml")
    }

    private val logger by loggerFactory()

    fun readSysMLModel() {
        check(resourceSet.resources.isEmpty()) {
            "Read SysML model must not be called multiple times!"
        }

        logger.info { "Reading sysml.library..." }
        SysMLLibraryUtil.setModelLibraryDirectory(libraryFile.path)

        readAll(libraryFile, false)

        logger.info { "Reading model..." }
        readAll(modelPath, true)

        logger.info { "Transforming elements..." }
        transformAll(true)

        logger.info { "Validating resources..." }
        validateAndLoadResourceSet(resourceSet)

        logger.info { "Model loaded!" }
    }

}
